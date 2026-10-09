package com.nexploy.campuslink.interview.service;

import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.interview.dto.InterviewResponse;
import com.nexploy.campuslink.interview.dto.RecordResultRequest;
import com.nexploy.campuslink.interview.dto.ScheduleInterviewRequest;
import com.nexploy.campuslink.interview.dto.UpdateInterviewRequest;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.event.InterviewCancelledEvent;
import com.nexploy.campuslink.interview.event.InterviewRescheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewScheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent;
import com.nexploy.campuslink.interview.exception.InterviewNotFoundException;
import com.nexploy.campuslink.interview.exception.InvalidInterviewTransitionException;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.shared.dto.PageResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final CampusAccessService campusAccessService;
    private final ApplicationService applicationService;
    private final DriveRepository driveRepository;
    private final StudentRepository studentRepository;
    private final RecruiterRepository recruiterRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Lists interviews visible to the current user. Admins see the whole campus scope
     * requested; every other role (officers included) is restricted to their own campus.
     */
    @Transactional(readOnly = true)
    public List<InterviewResponse> list(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's interviews.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        List<Interview> interviews;
        if (privileged) {
            interviews = campusId == null || campusId.isBlank()
                ? interviewRepository.findAll()
                : interviewRepository.findByCampusId(campusId);
        } else {
            interviews = interviewRepository.findByCampusId(user.getCampusId());
        }
        return interviews.stream().map(InterviewResponse::from).toList();
    }

    /** Paged variant of {@link #list(String)} — same campus-scoping, wrapped in a PageResponse envelope. */
    @Transactional(readOnly = true)
    public PageResponse<InterviewResponse> list(String campusId, Pageable pageable) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's interviews.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        Page<Interview> interviews;
        if (privileged) {
            interviews = campusId == null || campusId.isBlank()
                ? interviewRepository.findAll(pageable)
                : interviewRepository.findByCampusId(campusId, pageable);
        } else {
            interviews = interviewRepository.findByCampusId(user.getCampusId(), pageable);
        }
        return PageResponse.of(interviews, InterviewResponse::from);
    }

    @Transactional(readOnly = true)
    public InterviewResponse get(String interviewId) {
        UserAccount user = campusAccessService.currentUser();
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new InterviewNotFoundException(interviewId));
        campusAccessService.requireAccess(user, interview.getCampusId());
        return InterviewResponse.from(interview);
    }

    /**
     * Records the outcome of a completed interview. Officers/admins may record results for any
     * interview on an accessible campus; a recruiter only for interviews on their own drives
     * (the same recruiter-owns-drive rule as scheduling).
     */
    @Transactional
    public InterviewResponse recordResult(String interviewId, RecordResultRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new InterviewNotFoundException(interviewId));
        Drive drive = driveRepository.findById(interview.getDriveId())
            .orElseThrow(() -> new DriveNotFoundException(interview.getDriveId()));
        requireInterviewManager(user, drive);

        interview.setResult(request.result());
        interview.setFeedback(request.feedback());
        interview.setStatus(InterviewStatus.COMPLETED);
        interviewRepository.save(interview);

        // Drive the placement funnel from the recorded outcome (no-op if no matching
        // application exists). SELECTED advances to SELECTED; REJECTED closes the pipeline.
        // Either terminal outcome also notifies the student via an event.
        if (request.result() == InterviewResult.SELECTED) {
            applicationService.advance(interview.getStudentId(), interview.getDriveId(),
                ApplicationStatus.SELECTED, user.getUsername());
            eventPublisher.publishEvent(InterviewResultRecordedEvent.of(
                interview.getCampusId(), interview.getId(), interview.getStudentId(),
                interview.getRound(), interview.getCompanyName(), true, OffsetDateTime.now()));
        } else if (request.result() == InterviewResult.REJECTED) {
            applicationService.advance(interview.getStudentId(), interview.getDriveId(),
                ApplicationStatus.REJECTED, user.getUsername());
            eventPublisher.publishEvent(InterviewResultRecordedEvent.of(
                interview.getCampusId(), interview.getId(), interview.getStudentId(),
                interview.getRound(), interview.getCompanyName(), false, OffsetDateTime.now()));
        }
        return InterviewResponse.from(interview);
    }

    /**
     * Schedules a new interview slot. Officers/admins may schedule for any drive on an
     * accessible campus; a recruiter only for their own drives. Student and drive must exist
     * and share the drive's campus. The interview starts SCHEDULED.
     */
    @Transactional
    public InterviewResponse schedule(ScheduleInterviewRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Drive drive = driveRepository.findById(request.driveId())
            .orElseThrow(() -> new DriveNotFoundException(request.driveId()));
        requireInterviewManager(user, drive);

        Student student = studentRepository.findById(request.studentId())
            .orElseThrow(() -> new StudentNotFoundException(request.studentId()));
        if (!drive.getCampusId().equals(student.getCampusId())) {
            throw new IllegalArgumentException("Student " + student.getId()
                + " is not on the drive's campus");
        }

        Interview interview = Interview.builder()
            .id("int_" + UUID.randomUUID())
            .campusId(drive.getCampusId())
            .studentId(student.getId())
            .driveId(drive.getId())
            .scheduleEventId(request.scheduleEventId())
            .mode(request.mode())
            .scheduledAt(request.scheduledAt())
            .status(InterviewStatus.SCHEDULED)
            .result(InterviewResult.PENDING)
            .round(request.round())
            .venue(request.venue())
            .panel(request.panel())
            .companyName(request.companyName() != null ? request.companyName() : drive.getCompanyName())
            .createdAt(OffsetDateTime.now())
            .build();
        Interview saved = interviewRepository.save(interview);
        // Tell the student their interview is booked (in-app via the notification consumer).
        eventPublisher.publishEvent(InterviewScheduledEvent.of(
            saved.getCampusId(), saved.getId(), saved.getStudentId(), saved.getRound(),
            saved.getCompanyName(), saved.getScheduledAt(), OffsetDateTime.now()));
        return InterviewResponse.from(saved);
    }

    /** Reschedules a SCHEDULED interview. Applies only the non-null fields. */
    @Transactional
    public InterviewResponse reschedule(String interviewId, UpdateInterviewRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Interview interview = loadManageable(interviewId, user);
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw new InvalidInterviewTransitionException("Interview " + interviewId
                + " is " + interview.getStatus() + " and cannot be rescheduled");
        }
        if (request.scheduledAt() != null) {
            interview.setScheduledAt(request.scheduledAt());
        }
        if (request.mode() != null) {
            interview.setMode(request.mode());
        }
        if (request.round() != null) {
            interview.setRound(request.round());
        }
        if (request.venue() != null) {
            interview.setVenue(request.venue());
        }
        if (request.panel() != null) {
            interview.setPanel(request.panel());
        }
        Interview saved = interviewRepository.save(interview);
        // Tell the student the interview moved (in-app via the notification consumer).
        eventPublisher.publishEvent(InterviewRescheduledEvent.of(
            saved.getCampusId(), saved.getId(), saved.getStudentId(), saved.getRound(),
            saved.getCompanyName(), saved.getScheduledAt(), OffsetDateTime.now()));
        return InterviewResponse.from(saved);
    }

    /** Cancels a SCHEDULED interview (soft — the row and its history are preserved). */
    @Transactional
    public InterviewResponse cancel(String interviewId) {
        UserAccount user = campusAccessService.currentUser();
        Interview interview = loadManageable(interviewId, user);
        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            throw new InvalidInterviewTransitionException("Interview " + interviewId
                + " is COMPLETED and cannot be cancelled");
        }
        interview.setStatus(InterviewStatus.CANCELLED);
        Interview saved = interviewRepository.save(interview);
        // Tell the student the interview was cancelled (in-app via the notification consumer).
        eventPublisher.publishEvent(InterviewCancelledEvent.of(
            saved.getCampusId(), saved.getId(), saved.getStudentId(), saved.getRound(),
            saved.getCompanyName(), OffsetDateTime.now()));
        return InterviewResponse.from(saved);
    }

    // ---- Access helpers --------------------------------------------------------

    /** Loads an interview and asserts the user may manage it (officer/admin or owning recruiter). */
    private Interview loadManageable(String interviewId, UserAccount user) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new InterviewNotFoundException(interviewId));
        Drive drive = driveRepository.findById(interview.getDriveId())
            .orElseThrow(() -> new DriveNotFoundException(interview.getDriveId()));
        requireInterviewManager(user, drive);
        return interview;
    }

    private void requireInterviewManager(UserAccount user, Drive drive) {
        campusAccessService.requireAccess(user, drive.getCampusId());
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.RECRUITER) {
            Recruiter own = recruiterRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new AccessDeniedException("No recruiter profile for " + user.getUsername()));
            if (!own.getId().equals(drive.getRecruiterId())) {
                throw new AccessDeniedException("Recruiters can only manage interviews for their own drives");
            }
            return;
        }
        throw new AccessDeniedException("Only officers, admins and recruiters can manage interviews");
    }
}
