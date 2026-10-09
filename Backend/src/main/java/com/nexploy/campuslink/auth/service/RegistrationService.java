package com.nexploy.campuslink.auth.service;

import com.nexploy.campuslink.auth.dto.RecruiterRegisterRequest;
import com.nexploy.campuslink.auth.dto.StudentRegisterRequest;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.exception.UsernameAlreadyExistsException;
import com.nexploy.campuslink.auth.otp.service.OtpService;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotInDirectoryException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account creation for the two public roles, STUDENT and RECRUITER. Staff roles
 * (OFFICER/MENTOR/ADMIN) have no registration path at all — they are invite-only and created by an
 * existing officer/admin — so the privilege-escalation guard here is structural: there is simply
 * no method to self-register them.
 *
 * <p>Student registration is directory-gated (V60): the officer-managed campus directory supplies
 * the student row, and registration only CLAIMS that row (creates the account) after the
 * registrant proves email ownership with an OTP — no second Student row is ever created. Recruiter
 * registration still creates both rows in one transaction. Both are bound to an existing, active
 * campus for scope enforcement.
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserAccountRepository userAccountRepository;
    private final CampusRepository campusRepository;
    private final StudentRepository studentRepository;
    private final RecruiterRepository recruiterRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    @Transactional
    public UserAccount registerStudent(StudentRegisterRequest request) {
        requireActiveCampus(request.campusId());
        otpService.verifyAndConsume(request.email(), request.campusId(), request.otp());

        Student existingStudent = studentRepository
            .findByCampusIdAndEmailIgnoreCase(request.campusId(), request.email())
            .orElseThrow(StudentNotInDirectoryException::new);
        if (!Boolean.TRUE.equals(existingStudent.getActive())) {
            throw new IllegalArgumentException(
                "Your directory record is deactivated. Contact your placement office.");
        }
        claimDirectoryRecord(existingStudent, request);
        return createAccount(request.email(), request.password(), request.campusId(), UserRole.STUDENT);
    }

    /**
     * Claims the existing student row at registration (V60). This includes preloaded legacy or
     * seeded rows as long as the email has no account and the student verifies inbox ownership.
     * Officer-owned fields — name, roll number,
     * branch, academics — are NEVER overwritten; registration only fills blank contact info,
     * applies the frontend contract defaults where the officer left a hole, and normalizes the
     * email's case so {@code email == username} holds byte-for-byte (every /me resolution site
     * looks the student up by the exact username). The source field is preserved as provenance.
     */
    private void claimDirectoryRecord(Student record, StudentRegisterRequest request) {
        if (record.getPhone() == null || record.getPhone().isBlank()) {
            record.setPhone(request.phone());
        }
        record.setEmail(request.email());
        if (record.getRollNumber() == null || record.getRollNumber().isBlank()) {
            record.setRollNumber(record.getId());
        }
        if (record.getCgpa() == null) {
            record.setCgpa(BigDecimal.ZERO);
        }
        if (record.getBacklogs() == null) {
            record.setBacklogs(0);
        }
        if (record.getReadinessScore() == null) {
            record.setReadinessScore(0);
        }
        if (record.getPlacementStatus() == null) {
            record.setPlacementStatus("UNPLACED");
        }
        studentRepository.save(record);
    }

    @Transactional
    public UserAccount registerRecruiter(RecruiterRegisterRequest request) {
        requireActiveCampus(request.campusId());
        UserAccount account = createAccount(request.email(), request.password(), request.campusId(), UserRole.RECRUITER);

        String recruiterId = "rec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        recruiterRepository.save(Recruiter.builder()
            .id(recruiterId)
            .campusId(request.campusId())
            .name(request.fullName())
            .email(request.email())
            .phone(request.phone())
            .companyName(request.companyName())
            .companyWebsite(request.companyWebsite())
            .companyIndustry(request.companyIndustry())
            .companyLocation(request.companyLocation())
            .designation(request.designation())
            .active(true)
            .build());
        return account;
    }

    private void requireActiveCampus(String campusId) {
        Campus campus = campusRepository.findById(campusId)
            .orElseThrow(() -> new IllegalArgumentException("Campus not found: " + campusId));
        if (!campus.isActive()) {
            throw new IllegalArgumentException("Campus is not accepting registrations: " + campusId);
        }
    }

    private UserAccount createAccount(String email, String rawPassword, String campusId, UserRole role) {
        if (userAccountRepository.existsByUsernameIgnoreCase(email)) {
            throw new UsernameAlreadyExistsException(email);
        }
        return userAccountRepository.save(UserAccount.builder()
            .username(email)
            .password(passwordEncoder.encode(rawPassword))
            .campusId(campusId)
            .role(role)
            .enabled(true)
            .build());
    }
}
