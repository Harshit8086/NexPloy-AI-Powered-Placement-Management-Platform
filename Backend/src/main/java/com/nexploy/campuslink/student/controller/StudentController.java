package com.nexploy.campuslink.student.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.student.dto.AssessmentScores;
import com.nexploy.campuslink.student.dto.BacklogHistoryEntryResponse;
import com.nexploy.campuslink.student.dto.CareerRecommendationResponse;
import com.nexploy.campuslink.student.dto.ChangePlacementStatusRequest;
import com.nexploy.campuslink.student.dto.CsvImportResult;
import com.nexploy.campuslink.student.dto.RecordBacklogHistoryRequest;
import com.nexploy.campuslink.student.dto.DigitalTwinResponse;
import com.nexploy.campuslink.student.dto.InterviewPrepResponse;
import com.nexploy.campuslink.student.dto.LearningRecommendationResponse;
import com.nexploy.campuslink.student.dto.ReadinessSummaryResponse;
import com.nexploy.campuslink.student.dto.ResumeAnalysisDetail;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse;
import com.nexploy.campuslink.student.dto.ResumeAnalysisSummary;
import com.nexploy.campuslink.student.dto.ResumeIntelligenceResponse;
import com.nexploy.campuslink.student.dto.RoadmapProjectRequest;
import com.nexploy.campuslink.student.dto.SetStudentActiveRequest;
import com.nexploy.campuslink.student.dto.StudentDirectoryCreateRequest;
import com.nexploy.campuslink.student.dto.StudentDirectoryUpdateRequest;
import com.nexploy.campuslink.student.dto.RoadmapResponse;
import com.nexploy.campuslink.student.dto.RoadmapTaskStatusRequest;
import com.nexploy.campuslink.student.dto.StudentResponse;
import com.nexploy.campuslink.student.dto.StudentUpdateRequest;
import com.nexploy.campuslink.student.dto.WhatIfFactorResponse;
import com.nexploy.campuslink.student.dto.WhatIfRequest;
import com.nexploy.campuslink.student.dto.WhatIfResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.service.StudentService;
import com.nexploy.campuslink.student.service.StudentDirectoryService;
import com.nexploy.campuslink.student.service.CareerRecommendationService;
import com.nexploy.campuslink.student.service.InterviewPrepService;
import com.nexploy.campuslink.student.service.LearningRecommendationService;
import com.nexploy.campuslink.student.service.ResumeIntelligenceService;
import com.nexploy.campuslink.student.service.RoadmapService;
import com.nexploy.campuslink.student.service.StudentBacklogHistoryService;
import com.nexploy.campuslink.student.service.StudentProfileNormalizationService;
import com.nexploy.campuslink.readiness.event.StudentReadinessInputChangedEvent;
import com.nexploy.campuslink.shared.dto.PageRequests;
import com.nexploy.campuslink.shared.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final StudentDirectoryService directoryService;
    private final CampusAccessService campusAccessService;
    private final CareerRecommendationService careerRecommendationService;
    private final InterviewPrepService interviewPrepService;
    private final ResumeIntelligenceService resumeIntelligenceService;
    private final RoadmapService roadmapService;
    private final LearningRecommendationService learningRecommendationService;
    private final StudentProfileNormalizationService profileNormalizationService;
    private final StudentBacklogHistoryService backlogHistoryService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ResponseEntity<?> listStudents(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's students; officers are scoped to their own campus.
        boolean staffWide = user.getRole() == UserRole.ADMIN;

        // Opt-in pagination: only switch to the PageResponse envelope when the caller asks for it,
        // so existing array-consuming clients keep the legacy shape.
        if (PageRequests.requested(page, size)) {
            Pageable pageable = PageRequests.of(page, size, sort);
            Page<Student> result = staffWide
                ? studentRepository.findAll(pageable)
                : studentRepository.findByCampusId(user.getCampusId(), pageable);
            return ResponseEntity.ok(PageResponse.of(result, studentService::toResponse));
        }

        List<StudentResponse> all = (staffWide
            ? studentRepository.findAll()
            : studentRepository.findByCampusId(user.getCampusId()))
            .stream().map(studentService::toResponse).toList();
        return ResponseEntity.ok(all);
    }

    // ---- Directory management (V60): officer/admin only ----

    /**
     * Officer/admin adds one record to the campus directory (V60). The record carries
     * {@code source=DIRECTORY} and no account — it waits until the student registers with the
     * matching email and claims it. Duplicate campus+email is a 409.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse createDirectoryRecord(@Valid @RequestBody StudentDirectoryCreateRequest request) {
        UserAccount user = requireDirectoryManager();
        // The target campus arrives in the request body — pin the write to a campus the caller can reach.
        campusAccessService.requireAccess(user, request.campusId());
        return studentService.toResponse(directoryService.create(request));
    }

    /**
     * Officer/admin bulk-imports directory records from a CSV (V60). Rows are validated
     * independently — a bad or duplicate row is skipped with a reason and never aborts the rest.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResult importDirectoryCsv(
        @RequestParam("campusId") String campusId,
        @RequestParam("file") MultipartFile file
    ) {
        UserAccount user = requireDirectoryManager();
        // The campus param is client-supplied — pin the import to a campus the caller can reach.
        campusAccessService.requireAccess(user, campusId);
        return directoryService.importCsv(campusId, file);
    }

    /**
     * Officer/admin edits the directory-owned identity/academic fields of a student record (V60).
     * The email is immutable once the record is claimed (linked account) — a rename attempt is a
     * 409.
     */
    @PutMapping("/{studentId}/directory")
    public StudentResponse updateDirectoryRecord(
        @PathVariable String studentId,
        @Valid @RequestBody StudentDirectoryUpdateRequest request
    ) {
        Student student = loadManageable(studentId);
        return studentService.toResponse(directoryService.update(student, request));
    }

    /**
     * Officer/admin deactivates/reactivates a directory record (V60). Soft-active only — child
     * rows keep their FK targets. Deactivation also disables a linked account (login → 401).
     */
    @PatchMapping("/{studentId}/active")
    public StudentResponse setStudentActive(
        @PathVariable String studentId,
        @Valid @RequestBody SetStudentActiveRequest request
    ) {
        Student student = loadManageable(studentId);
        return studentService.toResponse(directoryService.setActive(student, request.active()));
    }

    /** Guards the directory entry points: only officers/admins may create or import records. */
    private UserAccount requireDirectoryManager() {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage the student directory");
        }
        return user;
    }

    /** The signed-in student's own profile (username == email for STUDENT accounts). */
    @GetMapping("/me")
    public StudentResponse getOwnProfile() {
        UserAccount user = campusAccessService.currentUser();
        var student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return studentService.toResponse(student);
    }

    /**
     * The signed-in student edits their own profile. Resolves the record by email (== username),
     * so a student can only ever update their own row. Derived/identity fields are not part of the
     * request body and are left untouched; a partial patch is fine (null = unchanged).
     */
    @PutMapping("/me")
    public StudentResponse updateOwnProfile(@Valid @RequestBody StudentUpdateRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        Integer previousBacklogs = student.getBacklogs();
        boolean readinessInputChanged = studentService.applyUpdate(student, request);
        Student saved = studentRepository.save(student);
        // Dual-write: mirror projects into the normalized tables (V40).
        profileNormalizationService.sync(saved, request);
        // Append a backlog-timeline point when the count changed (descriptive history).
        backlogHistoryService.recordChange(saved, previousBacklogs, user.getUsername());
        // Keep readiness continuously updated: recompute off-request when an input actually changed.
        if (readinessInputChanged) {
            eventPublisher.publishEvent(
                new StudentReadinessInputChangedEvent(saved.getId(), saved.getCampusId()));
        }
        return studentService.toResponse(saved);
    }

    /**
     * The signed-in student's read-only readiness summary (overall score + five-factor
     * breakdown), derived from their stored derived-score columns. Resolved by email == username,
     * so a student only ever reads their own readiness.
     */
    @GetMapping("/me/readiness")
    public ReadinessSummaryResponse getOwnReadiness() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return studentService.readinessSummary(student);
    }

    /**
     * The signed-in student's read-only digital-twin summary (headline readiness score + status
     * label), derived from their stored readiness score. Resolved by email == username, so a
     * student only ever reads their own twin.
     */
    @GetMapping("/me/digital-twin")
    public DigitalTwinResponse getOwnDigitalTwin() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return studentService.digitalTwin(student);
    }

    /**
     * The signed-in student's read-only assessment baseline (five 0-100 scores), parsed from their
     * stored assessment JSON. Resolved by email == username; scores are authoritative and not
     * writable through this path.
     */
    @GetMapping("/me/assessments")
    public AssessmentScores getOwnAssessments() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return studentService.assessments(student);
    }

    /**
     * The signed-in student's backlog timeline, newest first — every recorded change to their active
     * backlog count. Resolved by email == username, so a student only ever reads their own history.
     */
    @GetMapping("/me/backlog-history")
    public List<BacklogHistoryEntryResponse> getOwnBacklogHistory() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return backlogHistoryService.list(student.getId()).stream()
            .map(BacklogHistoryEntryResponse::from)
            .toList();
    }

    /**
     * The signed-in student's read-only resume-intelligence summary shown before any upload: the
     * stored resume score, recorded strengths and recorded gaps only. Weaknesses, suggestions and the
     * recommendation are produced per-resume by {@code POST /me/resume-intelligence/analyze}.
     * Resolved by email == username; nothing here is writable.
     */
    @GetMapping("/me/resume-intelligence")
    public ResumeIntelligenceResponse getOwnResumeIntelligence() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return studentService.resumeIntelligence(student);
    }

    /**
     * Analyzes an uploaded resume file for the signed-in student and returns a resume-specific
     * intelligence report (score + strengths + missing keywords + weaknesses + project suggestions +
     * recommendation), derived from the resume's actual content. In {@code llm} mode the prose is
     * written by the grounded model; the score and keywords stay deterministic. Resolved by
     * email == username. Stateless — nothing is persisted.
     */
    @PostMapping(value = "/me/resume-intelligence/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeAnalysisResponse analyzeOwnResume(@RequestParam("file") MultipartFile file) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return resumeIntelligenceService.analyzeUploadStructured(file, student);
    }

    /**
     * The signed-in student's resume Analysis History — every prior analyze run, newest first, as
     * lightweight summaries (id + resume filename + headline score + timestamp) for the history list
     * and progress-over-time view. Resolved by email == username, so a student only ever sees their
     * own runs; no runs yet yields an empty list.
     */
    @GetMapping("/me/resume-analysis/history")
    public List<ResumeAnalysisSummary> getOwnResumeAnalysisHistory() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return resumeIntelligenceService.history(student);
    }

    /**
     * One past resume analysis, restored in full so the UI can re-render every section without
     * recomputing. Resolved by email == username and scoped to the owner: a missing id, or one
     * belonging to another student, yields 404.
     */
    @GetMapping("/me/resume-analysis/history/{analysisId}")
    public ResumeAnalysisDetail getOwnResumeAnalysisDetail(@PathVariable String analysisId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return resumeIntelligenceService.historyDetail(student, analysisId);
    }

    /**
     * The signed-in student's personalized Career Roadmap — gap-driven phases and tasks with computed
     * progress. Materialized from the curated catalog on first view and seeded against the skills the
     * student has already demonstrated. Resolved by email == username, so a student only ever reads
     * their own roadmap.
     */
    @GetMapping("/me/roadmap")
    public RoadmapResponse getOwnRoadmap() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return roadmapService.getRoadmap(student);
    }

    /**
     * Marks one roadmap task PLANNED/DONE for the signed-in student and returns the recomputed
     * roadmap. Resolved by email == username; an unknown task id is a 400. Progress is recomputed
     * from real task status — never fabricated.
     */
    @PutMapping("/me/roadmap/tasks/{taskId}")
    public RoadmapResponse updateOwnRoadmapTask(
        @PathVariable String taskId, @Valid @RequestBody RoadmapTaskStatusRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return roadmapService.toggleTask(student, taskId, request.status());
    }

    /**
     * Adds a portfolio project to the signed-in student's roadmap (idempotent by title) and returns
     * the recomputed roadmap. Used by the resume analysis "Add to Roadmap" action and the roadmap's
     * recommended-project cards. Resolved by email == username.
     */
    @PostMapping("/me/roadmap/projects")
    public RoadmapResponse addOwnRoadmapProject(@Valid @RequestBody RoadmapProjectRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return roadmapService.addProject(student, request);
    }

    /**
     * Rebuilds the signed-in student's roadmap from the current catalog and their latest demonstrated
     * skills while preserving completed tasks and their portfolio projects — so a newly-recorded skill
     * (e.g. from completing a project) flips its pending gaps to done. Resolved by email == username;
     * progress stays grounded in real task status.
     */
    @PostMapping("/me/roadmap/regenerate")
    public RoadmapResponse regenerateOwnRoadmap() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return roadmapService.regenerate(student);
    }

    /**
     * The signed-in student's Learning Recommendations — one per still-pending skill gap on their
     * Career Roadmap, each paired with curated (never AI-invented) resources and a related roadmap
     * milestone. Resolved by email == username; skills already demonstrated are omitted because their
     * roadmap tasks are done, so this never tells a student to relearn what they can already do.
     */
    @GetMapping("/me/learning-recommendations")
    public List<LearningRecommendationResponse> getOwnLearningRecommendations() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return learningRecommendationService.recommend(student);
    }

    /**
     * Retrieves dynamic What-If simulation factors for the signed-in student, derived from their
     * Career Roadmap tasks, pending skill gaps, and practice milestones.
     */
    @GetMapping("/me/what-if/factors")
    public List<WhatIfFactorResponse> getOwnWhatIfFactors() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return roadmapService.getWhatIfFactors(student);
    }

    /**
     * Computes the signed-in student's authoritative What-If projection for a set of growth actions.
     * Resolved by email == username; the baseline is the student's own stored placement probability
     * and the projection is computed server-side (the client never invents the numbers). This does
     * not persist anything — applying a roadmap stays on PUT /me/goals.
     */
    @PostMapping("/me/what-if")
    public WhatIfResponse computeOwnWhatIf(@Valid @RequestBody WhatIfRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return studentService.whatIf(student, request.factors());
    }

    /**
     * The signed-in student's deterministic career recommendations: their own campus's open drives
     * ranked by skill/eligibility/role fit. Resolved by email == username; every signal is derived
     * from real stored data, so an empty campus yields an empty list rather than fabricated matches.
     */
    @GetMapping("/me/career-recommendations")
    public List<CareerRecommendationResponse> getOwnCareerRecommendations() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return careerRecommendationService.recommend(student);
    }

    /**
     * The signed-in student's deterministic interview-preparation plans for the drives they have
     * applied to. Resolved by email == username; topic coverage and preparation scores are derived
     * from the student's recorded skill proficiencies against each drive's required skills, so no
     * applications yields an empty list rather than fabricated plans.
     */
    @GetMapping("/me/interview-prep")
    public List<InterviewPrepResponse> getOwnInterviewPrep() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        return interviewPrepService.plansFor(student);
    }

    @GetMapping("/{studentId}")
    public StudentResponse getStudent(@PathVariable String studentId) {
        UserAccount user = campusAccessService.currentUser();
        var student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));

        campusAccessService.requireAccess(user, student.getCampusId());
        return studentService.toResponse(student);
    }

    /**
     * A student's backlog timeline for staff viewing a specific student (e.g. an officer on the Risk
     * Radar or roster). Campus-scoped: the caller must have access to the student's campus.
     */
    @GetMapping("/{studentId}/backlog-history")
    public List<BacklogHistoryEntryResponse> getStudentBacklogHistory(@PathVariable String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return backlogHistoryService.list(student.getId()).stream()
            .map(BacklogHistoryEntryResponse::from)
            .toList();
    }

    /**
     * Officer/admin appends a manual backlog-timeline entry for a student in their campus reach (e.g.
     * recording that backlogs were cleared in a given semester). Descriptive only — it does not change
     * the student's current {@code backlogs} count, which is managed via the profile edit endpoints.
     */
    @PostMapping("/{studentId}/backlog-history")
    public BacklogHistoryEntryResponse addStudentBacklogHistory(
        @PathVariable String studentId,
        @Valid @RequestBody RecordBacklogHistoryRequest request
    ) {
        Student student = loadManageable(studentId);
        return BacklogHistoryEntryResponse.from(backlogHistoryService.record(
            student, request.semester(), request.activeBacklogs(), request.clearedBacklogs(),
            request.note(), campusAccessService.currentUser().getUsername()));
    }

    /**
     * Officer/admin edit of any student within their campus reach. Same bounded, self-reportable
     * fields as PUT /me (the AI/derived signals and identity/scope stay untouched); campus access is
     * enforced and the projects dual-write is mirrored.
     */
    @PutMapping("/{studentId}")
    public StudentResponse updateStudent(
        @PathVariable String studentId,
        @Valid @RequestBody StudentUpdateRequest request
    ) {
        Student student = loadManageable(studentId);
        Integer previousBacklogs = student.getBacklogs();
        boolean readinessInputChanged = studentService.applyUpdate(student, request);
        Student saved = studentRepository.save(student);
        profileNormalizationService.sync(saved, request);
        backlogHistoryService.recordChange(saved, previousBacklogs, campusAccessService.currentUser().getUsername());
        if (readinessInputChanged) {
            eventPublisher.publishEvent(
                new StudentReadinessInputChangedEvent(saved.getId(), saved.getCampusId()));
        }
        return studentService.toResponse(saved);
    }

    /** Officer/admin sets a student's placement status (UNPLACED/IN PROCESS/OFFERED/PLACED). */
    @PatchMapping("/{studentId}/placement-status")
    public StudentResponse changePlacementStatus(
        @PathVariable String studentId,
        @Valid @RequestBody ChangePlacementStatusRequest request
    ) {
        Student student = loadManageable(studentId);
        studentService.changePlacementStatus(student, request.placementStatus());
        return studentService.toResponse(studentRepository.save(student));
    }

    /** Loads a student for officer/admin management, enforcing role and campus access. */
    private Student loadManageable(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage students");
        }
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return student;
    }
}
