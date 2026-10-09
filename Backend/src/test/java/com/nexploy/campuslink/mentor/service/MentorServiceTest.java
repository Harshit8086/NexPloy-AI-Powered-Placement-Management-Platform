package com.nexploy.campuslink.mentor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.mentor.dto.CreateAssignmentRequest;
import com.nexploy.campuslink.mentor.dto.CreateNoteRequest;
import com.nexploy.campuslink.mentor.dto.MentorNoteResponse;
import com.nexploy.campuslink.mentor.entity.MentorNote;
import com.nexploy.campuslink.mentor.exception.StudentNotAssignedException;
import com.nexploy.campuslink.mentor.repository.MentorAssignmentRepository;
import com.nexploy.campuslink.mentor.repository.MentorNoteRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class MentorServiceTest {

    private final MentorAssignmentRepository assignmentRepository =
        org.mockito.Mockito.mock(MentorAssignmentRepository.class);
    private final MentorNoteRepository noteRepository = org.mockito.Mockito.mock(MentorNoteRepository.class);
    private final StudentRepository studentRepository = org.mockito.Mockito.mock(StudentRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final UserAccountRepository userAccountRepository =
        org.mockito.Mockito.mock(UserAccountRepository.class);
    private final PasswordEncoder passwordEncoder =
        org.mockito.Mockito.mock(PasswordEncoder.class);
    private final MentorService service =
        new MentorService(assignmentRepository, noteRepository, studentRepository, campusAccessService, userAccountRepository, passwordEncoder);

    @Test
    void assignedMentorCanAddNote() {
        authenticate(user("mentor", UserRole.MENTOR, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo")));
        when(assignmentRepository.existsByMentorUsernameAndStudentId("mentor", "stu_demo")).thenReturn(true);
        when(noteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MentorNoteResponse response = service.addNote("stu_demo", new CreateNoteRequest("Doing well"));

        assertThat(response.authorUsername()).isEqualTo("mentor");
        assertThat(response.studentId()).isEqualTo("stu_demo");
    }

    @Test
    void unassignedMentorCannotAddNote() {
        authenticate(user("mentor", UserRole.MENTOR, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo")));
        when(assignmentRepository.existsByMentorUsernameAndStudentId("mentor", "stu_demo")).thenReturn(false);

        assertThatThrownBy(() -> service.addNote("stu_demo", new CreateNoteRequest("Should fail")))
            .isInstanceOf(StudentNotAssignedException.class);
    }

    @Test
    void unassignedMentorCannotListNotes() {
        authenticate(user("mentor", UserRole.MENTOR, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo")));
        when(assignmentRepository.existsByMentorUsernameAndStudentId("mentor", "stu_demo")).thenReturn(false);

        assertThatThrownBy(() -> service.listNotes("stu_demo"))
            .isInstanceOf(StudentNotAssignedException.class);
    }

    @Test
    void officerCanAccessAnyStudentInCampusWithoutAssignment() {
        authenticate(user("officer", UserRole.OFFICER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo")));
        when(noteRepository.findByStudentIdOrderByCreatedAtDesc("stu_demo"))
            .thenReturn(List.of(MentorNote.builder().id("n1").studentId("stu_demo").campusId("campus_demo")
                .authorUsername("mentor").note("x").build()));

        List<MentorNoteResponse> notes = service.listNotes("stu_demo");

        assertThat(notes).hasSize(1);
    }

    @Test
    void mentorCannotAssign() {
        authenticate(user("mentor", UserRole.MENTOR, "campus_demo"));

        assertThatThrownBy(() -> service.assign(new CreateAssignmentRequest("mentor", "stu_demo")))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void officerAssignsMentorToStudent() {
        authenticate(user("officer", UserRole.OFFICER, "campus_demo"));
        when(studentRepository.findById("stu_demo")).thenReturn(Optional.of(student("campus_demo")));
        when(assignmentRepository.existsByMentorUsernameAndStudentId("mentor", "stu_demo")).thenReturn(false);
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.assign(new CreateAssignmentRequest("mentor", "stu_demo"));

        assertThat(response.mentorUsername()).isEqualTo("mentor");
        assertThat(response.campusId()).isEqualTo("campus_demo");
    }

    private Student student(String campusId) {
        return Student.builder()
            .id("stu_demo")
            .campusId(campusId)
            .name("Demo Student")
            .email("demo@student.example")
            .rollNumber("DEMO-001")
            .branch("CSE")
            .branchCode("CS")
            .graduationYear(2026)
            .cgpa(new BigDecimal("8.2"))
            .backlogs(0)
            .readinessScore(80)
            .placementStatus("ACTIVE")
            .build();
    }

    private UserAccount user(String username, UserRole role, String campusId) {
        return UserAccount.builder()
            .username(username)
            .password("{noop}secret")
            .campusId(campusId)
            .role(role)
            .enabled(true)
            .build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
