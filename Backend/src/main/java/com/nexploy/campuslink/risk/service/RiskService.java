package com.nexploy.campuslink.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.entity.MockInterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.interview.repository.MockInterviewRepository;
import com.nexploy.campuslink.risk.client.MlRiskClient;
import com.nexploy.campuslink.risk.client.MlRiskClient.RiskPrediction;
import com.nexploy.campuslink.risk.client.MlRiskClient.RiskPredictRequest;
import com.nexploy.campuslink.risk.client.MlRiskClient.Signals;
import com.nexploy.campuslink.risk.dto.RiskEntryResponse;
import com.nexploy.campuslink.risk.dto.RiskEntryResponse.RiskFactorView;
import com.nexploy.campuslink.risk.dto.RiskPredictRequestBody;
import com.nexploy.campuslink.risk.entity.RiskEntry;
import com.nexploy.campuslink.risk.event.StudentRiskDetectedEvent;
import com.nexploy.campuslink.risk.repository.RiskEntryRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates placement-risk prediction and persists it as append-only history. Spring is the
 * system of record: it resolves the student, enforces campus access, reads governed fields
 * (readiness, backlogs) from the record, and forwards behavioural signals to the stateless ML
 * service. The prediction is support-oriented and never drives an exclusion decision. Each call
 * writes a {@link RiskEntry} (immutable history); a high-band entry is what an officer turns into
 * a tracked intervention plan (see {@link InterventionService}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskService {

    private final StudentRepository studentRepository;
    private final RiskEntryRepository riskEntryRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final MockInterviewRepository mockInterviewRepository;
    private final CampusAccessService campusAccessService;
    private final MlRiskClient mlRiskClient;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${ml.policy-version:policy-2026-01}")
    private String modelPolicyVersion;

    @Transactional
    public RiskPrediction predict(String studentId, String placementCycleId, RiskPredictRequestBody body) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());

        Double readiness = student.getReadinessScore() == null ? null : student.getReadinessScore().doubleValue();
        RiskPredictRequestBody signals = resolveActivitySignals(studentId, body);
        RiskPredictRequest request = new RiskPredictRequest(
            UUID.randomUUID().toString(),
            student.getCampusId(),
            placementCycleId,
            modelPolicyVersion,
            studentId,
            new Signals(
                readiness,
                student.getBacklogs(),
                signals.applicationsSubmitted(),
                signals.interviewsAttended(),
                signals.interviewsMissed(),
                signals.daysSinceLastActivity(),
                signals.mockInterviewsCompleted()
            )
        );
        RiskPrediction prediction = mlRiskClient.predict(request);
        RiskEntry entry = persistEntry(student, placementCycleId, prediction);
        // The ML service's high-support band is NEEDS_SUPPORT (bands: LOW / MONITOR /
        // NEEDS_SUPPORT / INSUFFICIENT_DATA); "HIGH" is also accepted for legacy/mock data.
        if ("NEEDS_SUPPORT".equalsIgnoreCase(entry.getLevel()) || "HIGH".equalsIgnoreCase(entry.getLevel())) {
            eventPublisher.publishEvent(StudentRiskDetectedEvent.of(
                entry.getCampusId(), entry.getStudentId(), entry.getId(), entry.getLevel(),
                entry.getSupportProbability(), entry.getGeneratedAt()));
        }
        return prediction;
    }

    private RiskPredictRequestBody resolveActivitySignals(String studentId, RiskPredictRequestBody supplied) {
        List<Application> applications = applicationRepository.findByStudentId(studentId);
        List<Interview> interviews = interviewRepository.findByStudentId(studentId);
        var mockInterviews = mockInterviewRepository.findByStudentIdOrderByCreatedAtDesc(studentId);

        LocalDate latestActivity = applications.stream()
            .map(Application::getAppliedOn)
            .filter(date -> date != null && !date.isAfter(LocalDate.now()))
            .max(LocalDate::compareTo)
            .orElse(null);

        long attended = 0;
        long missed = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (Interview interview : interviews) {
            if (interview.getStatus() == InterviewStatus.COMPLETED) {
                attended++;
            } else if (interview.getStatus() == InterviewStatus.NO_SHOW) {
                missed++;
            }
            OffsetDateTime scheduledAt = interview.getScheduledAt();
            if (interview.getStatus() != InterviewStatus.CANCELLED
                && scheduledAt != null && !scheduledAt.isAfter(now)) {
                LocalDate activityDate = scheduledAt.toLocalDate();
                if (latestActivity == null || activityDate.isAfter(latestActivity)) {
                    latestActivity = activityDate;
                }
            }
        }

        long completedMockInterviews = mockInterviews.stream()
            .filter(mock -> mock.getStatus() == MockInterviewStatus.COMPLETED)
            .count();
        for (var mock : mockInterviews) {
            OffsetDateTime updatedAt = mock.getUpdatedAt();
            if (mock.getStatus() == MockInterviewStatus.COMPLETED
                && updatedAt != null && !updatedAt.isAfter(now)) {
                LocalDate activityDate = updatedAt.toLocalDate();
                if (latestActivity == null || activityDate.isAfter(latestActivity)) {
                    latestActivity = activityDate;
                }
            }
        }

        Integer daysSinceActivity = latestActivity == null
            ? null
            : Math.toIntExact(ChronoUnit.DAYS.between(latestActivity, LocalDate.now()));
        return new RiskPredictRequestBody(
            supplied.applicationsSubmitted() == null
                ? applications.size() : supplied.applicationsSubmitted(),
            supplied.interviewsAttended() == null
                ? Math.toIntExact(attended) : supplied.interviewsAttended(),
            supplied.interviewsMissed() == null
                ? Math.toIntExact(missed) : supplied.interviewsMissed(),
            supplied.daysSinceLastActivity() == null ? daysSinceActivity : supplied.daysSinceLastActivity(),
            supplied.mockInterviewsCompleted() == null
                ? Math.toIntExact(completedMockInterviews) : supplied.mockInterviewsCompleted()
        );
    }

    @Transactional(readOnly = true)
    public List<RiskEntryResponse> listEntries(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return riskEntryRepository.findByStudentIdOrderByGeneratedAtDesc(studentId).stream()
            .map(this::toResponse).toList();
    }

    /**
     * The latest risk entry per student across a campus — the Risk Radar roll-up. Entries come
     * back newest-first, so the first one seen per student is that student's current risk.
     */
    @Transactional(readOnly = true)
    public List<RiskEntryResponse> listCampusEntries(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);
        Map<String, RiskEntry> latestByStudent = new LinkedHashMap<>();
        for (RiskEntry entry : riskEntryRepository.findByCampusIdOrderByGeneratedAtDesc(campusId)) {
            latestByStudent.putIfAbsent(entry.getStudentId(), entry);
        }
        return latestByStudent.values().stream().map(this::toResponse).toList();
    }

    /**
     * Runs risk prediction across all students on a campus and saves updated risk entries.
     */
    @Transactional
    public List<RiskEntryResponse> assessCampus(String campusId, String placementCycleId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);

        List<Student> students = studentRepository.findByCampusId(campusId);
        for (Student student : students) {
            try {
                Double readiness = student.getReadinessScore() == null ? null : student.getReadinessScore().doubleValue();
                RiskPredictRequestBody signals = resolveActivitySignals(
                    student.getId(),
                    new RiskPredictRequestBody(null, null, null, null, null)
                );
                RiskPredictRequest request = new RiskPredictRequest(
                    UUID.randomUUID().toString(),
                    student.getCampusId(),
                    placementCycleId,
                    modelPolicyVersion,
                    student.getId(),
                    new Signals(
                        readiness,
                        student.getBacklogs(),
                        signals.applicationsSubmitted(),
                        signals.interviewsAttended(),
                        signals.interviewsMissed(),
                        signals.daysSinceLastActivity(),
                        signals.mockInterviewsCompleted()
                    )
                );
                RiskPrediction prediction = mlRiskClient.predict(request);
                RiskEntry entry = persistEntry(student, placementCycleId, prediction);
                if ("NEEDS_SUPPORT".equalsIgnoreCase(entry.getLevel()) || "HIGH".equalsIgnoreCase(entry.getLevel())) {
                    eventPublisher.publishEvent(StudentRiskDetectedEvent.of(
                        entry.getCampusId(), entry.getStudentId(), entry.getId(), entry.getLevel(),
                        entry.getSupportProbability(), entry.getGeneratedAt()));
                }
            } catch (Exception ex) {
                log.warn("Failed to assess risk for student {}: {}", student.getId(), ex.getMessage());
            }
        }
        return listCampusEntries(campusId);
    }

    private RiskEntry persistEntry(Student student, String placementCycleId, RiskPrediction prediction) {
        RiskEntry entry = RiskEntry.builder()
            .id("risk_" + UUID.randomUUID())
            .studentId(student.getId())
            .campusId(student.getCampusId())
            .placementCycleId(placementCycleId)
            .level(prediction.band())
            .supportProbability(prediction.supportProbability())
            .primaryReason(primaryReason(prediction))
            .factorsJson(write(prediction.topFactors() == null ? List.of() : prediction.topFactors()))
            .recommendedInterventionsJson(
                write(prediction.recommendedInterventions() == null ? List.of() : prediction.recommendedInterventions()))
            .confidence(prediction.confidence())
            .modelVersion(prediction.modelVersion())
            .featureVersion(prediction.featureVersion())
            .generatedAt(prediction.generatedAt() == null ? OffsetDateTime.now() : prediction.generatedAt())
            .createdAt(OffsetDateTime.now())
            .build();
        return riskEntryRepository.save(entry);
    }

    private String primaryReason(RiskPrediction prediction) {
        if (prediction.topFactors() == null || prediction.topFactors().isEmpty()) {
            return "No dominant factor";
        }
        return prediction.topFactors().get(0).name();
    }

    private RiskEntryResponse toResponse(RiskEntry entry) {
        List<RiskFactorView> factors = readFactors(entry.getFactorsJson());
        List<String> interventions = readStrings(entry.getRecommendedInterventionsJson());
        return new RiskEntryResponse(
            entry.getId(), entry.getStudentId(), entry.getPlacementCycleId(), entry.getLevel(),
            entry.getSupportProbability(), entry.getPrimaryReason(), factors, interventions,
            entry.getConfidence(), entry.getModelVersion(), entry.getFeatureVersion(), entry.getGeneratedAt());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize risk entry field", exception);
        }
    }

    private List<RiskFactorView> readFactors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<RiskFactorView>>() {});
        } catch (JsonProcessingException exception) {
            // Display-only field on append-only history. A single malformed row must not 500 the
            // whole per-student list or campus roll-up — log and degrade to no factors instead.
            log.warn("Skipping unreadable risk factors JSON: {}", exception.getOriginalMessage());
            return List.of();
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            log.warn("Skipping unreadable recommended-interventions JSON: {}", exception.getOriginalMessage());
            return List.of();
        }
    }
}
