package com.nexploy.campuslink.mentor.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.exception.UsernameAlreadyExistsException;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.mentor.dto.CreateAssignmentRequest;
import com.nexploy.campuslink.mentor.dto.CreateMentorRequest;
import com.nexploy.campuslink.mentor.dto.CreateNoteRequest;
import com.nexploy.campuslink.mentor.dto.MentorAccountResponse;
import com.nexploy.campuslink.mentor.dto.MentorAssignmentResponse;
import com.nexploy.campuslink.mentor.dto.MentorNoteResponse;
import com.nexploy.campuslink.mentor.entity.MentorAssignment;
import com.nexploy.campuslink.mentor.entity.MentorNote;
import com.nexploy.campuslink.mentor.exception.StudentNotAssignedException;
import com.nexploy.campuslink.mentor.repository.MentorAssignmentRepository;
import com.nexploy.campuslink.mentor.repository.MentorNoteRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentorService {

    private final MentorAssignmentRepository assignmentRepository;
    private final MentorNoteRepository noteRepository;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Assigns a mentor to a student. Only officers and admins may create assignments, and
     * only within a campus they can access. The student must exist.
     */
    @Transactional
    public MentorAssignmentResponse assign(CreateAssignmentRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Student student = studentRepository.findById(request.studentId())
            .orElseThrow(() -> new StudentNotFoundException(request.studentId()));
        campusAccessService.requireAccess(user, student.getCampusId());

        if (assignmentRepository.existsByMentorUsernameAndStudentId(request.mentorUsername(), request.studentId())) {
            return assignmentRepository.findByMentorUsername(request.mentorUsername()).stream()
                .filter(existing -> existing.getStudentId().equals(request.studentId()))
                .findFirst()
                .map(MentorAssignmentResponse::from)
                .orElseThrow();
        }

        MentorAssignment assignment = assignmentRepository.save(MentorAssignment.builder()
            .id(UUID.randomUUID().toString())
            .mentorUsername(request.mentorUsername())
            .studentId(request.studentId())
            .campusId(student.getCampusId())
            .assignedAt(OffsetDateTime.now())
            .build());
        return MentorAssignmentResponse.from(assignment);
    }

    /** Lists the students assigned to the current mentor (or, for officers/admins, any mentor). */
    @Transactional(readOnly = true)
    public List<MentorAssignmentResponse> listAssignments(String mentorUsername) {
        UserAccount user = campusAccessService.currentUser();
        String target = resolveMentorScope(user, mentorUsername);
        return assignmentRepository.findByMentorUsername(target).stream()
            .map(MentorAssignmentResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<MentorNoteResponse> listNotes(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        requireStudentAccess(user, studentId);
        return noteRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .map(MentorNoteResponse::from)
            .toList();
    }

    /** Adds a mentoring note. The author must have access to the student. */
    @Transactional
    public MentorNoteResponse addNote(String studentId, CreateNoteRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = requireStudentAccess(user, studentId);

        MentorNote note = noteRepository.save(MentorNote.builder()
            .id(UUID.randomUUID().toString())
            .studentId(studentId)
            .campusId(student.getCampusId())
            .authorUsername(user.getUsername())
            .note(request.note())
            .createdAt(OffsetDateTime.now())
            .build());
        return MentorNoteResponse.from(note);
    }

    /**
     * Enforces the access rule for a student: officers and admins pass through campus access;
     * a mentor passes only if assigned to the student (SECURITY.md section 2); all other roles
     * are denied. Returns the student when access is granted.
     */
    private Student requireStudentAccess(UserAccount user, String studentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));

        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            campusAccessService.requireAccess(user, student.getCampusId());
            return student;
        }
        if (user.getRole() == UserRole.MENTOR
            && assignmentRepository.existsByMentorUsernameAndStudentId(user.getUsername(), studentId)) {
            return student;
        }
        throw new StudentNotAssignedException(user.getUsername(), studentId);
    }

    private String resolveMentorScope(UserAccount user, String requestedMentor) {
        boolean privileged = user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN;
        if (privileged && requestedMentor != null && !requestedMentor.isBlank()) {
            return requestedMentor;
        }
        if (user.getRole() == UserRole.MENTOR) {
            return user.getUsername();
        }
        if (privileged) {
            return user.getUsername();
        }
        throw new AccessDeniedException("Only mentors, officers, and admins can list assignments");
    }

    /** Lists active mentor accounts within the campus scope. Officers/Admins only. */
    @Transactional(readOnly = true)
    public List<MentorAccountResponse> listMentors(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);

        String targetCampus = (user.getRole() == UserRole.ADMIN && campusId != null && !campusId.isBlank())
            ? campusId
            : user.getCampusId();

        List<UserAccount> mentors = (targetCampus != null && !targetCampus.isBlank())
            ? userAccountRepository.findByRoleAndCampusIdAndEnabledTrue(UserRole.MENTOR, targetCampus)
            : userAccountRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.MENTOR && u.isEnabled())
                .toList();

        return mentors.stream()
            .map(m -> new MentorAccountResponse(
                m.getUsername(),
                m.getCampusId(),
                assignmentRepository.findByMentorUsername(m.getUsername()).size()
            ))
            .toList();
    }

    /** Creates a new Mentor UserAccount. Officers and Admins only. */
    @Transactional
    public MentorAccountResponse createMentorAccount(CreateMentorRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);

        String targetCampus = (user.getRole() == UserRole.ADMIN && request.campusId() != null && !request.campusId().isBlank())
            ? request.campusId()
            : user.getCampusId();

        campusAccessService.requireAccess(user, targetCampus);

        String email = request.email().trim();
        if (userAccountRepository.existsByUsernameIgnoreCase(email)) {
            throw new UsernameAlreadyExistsException(email);
        }

        UserAccount mentor = userAccountRepository.save(UserAccount.builder()
            .username(email)
            .password(passwordEncoder.encode(request.password()))
            .campusId(targetCampus)
            .role(UserRole.MENTOR)
            .enabled(true)
            .build());

        return new MentorAccountResponse(mentor.getUsername(), mentor.getCampusId(), 0);
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can assign mentors");
        }
    }
}
