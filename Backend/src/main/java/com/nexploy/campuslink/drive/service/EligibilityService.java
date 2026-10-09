package com.nexploy.campuslink.drive.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Deterministic, explainable eligibility. Mirrors the frontend engine
 * (src/lib/eligibility.ts): CGPA, branch, backlogs, graduation year, and — when the drive
 * sets them — 10th/12th percentage. Branch and the percentage floors are optional: a drive
 * that does not specify them (empty allowed-branches list / null minimum) does not add that
 * check, so a legacy drive without the enriched criteria still evaluates as before.
 */
@Service
@RequiredArgsConstructor
public class EligibilityService {

    private final StudentRepository studentRepository;
    private final DriveRepository driveRepository;
    private final ObjectMapper objectMapper;

    public EligibilityResponse evaluate(String driveId, String studentId) {
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        List<EligibilityResponse.EligibilityCheck> checks = new ArrayList<>();
        checks.add(check("CGPA", student.getCgpa().compareTo(drive.getMinimumCgpa()) >= 0,
            student.getCgpa() + " >= " + drive.getMinimumCgpa()));

        List<String> allowedBranches = readBranches(drive.getAllowedBranchesJson());
        if (!allowedBranches.isEmpty()) {
            boolean branchOk = allowedBranches.contains(student.getBranchCode());
            checks.add(check("Branch", branchOk,
                student.getBranchCode()
                    + (branchOk ? " is in allowed list (" : " not in allowed list (")
                    + String.join(", ", allowedBranches) + ")"));
        }

        checks.add(check("Backlogs", student.getBacklogs() <= drive.getMaximumBacklogs(),
            student.getBacklogs() + " <= " + drive.getMaximumBacklogs()));
        checks.add(check("Graduation year", student.getGraduationYear().equals(drive.getGraduationYear()),
            student.getGraduationYear() + " == " + drive.getGraduationYear()));

        if (drive.getMinimumTenthPercentage() != null) {
            checks.add(percentageCheck("10th Percentage",
                student.getTenthPercentage(), drive.getMinimumTenthPercentage()));
        }
        if (drive.getMinimumTwelfthPercentage() != null) {
            checks.add(percentageCheck("12th Percentage",
                student.getTwelfthPercentage(), drive.getMinimumTwelfthPercentage()));
        }

        List<String> failures = checks.stream()
            .filter(check -> !check.passed())
            .map(EligibilityResponse.EligibilityCheck::detail)
            .toList();
        return new EligibilityResponse(studentId, driveId, failures.isEmpty(), checks, failures);
    }

    private EligibilityResponse.EligibilityCheck percentageCheck(
        String label, BigDecimal actual, BigDecimal required
    ) {
        if (actual == null) {
            return check(label, false, "not on record; " + required + "% required");
        }
        boolean passed = actual.compareTo(required) >= 0;
        return check(label, passed, actual + "% " + (passed ? ">= " : "< ") + required + "% required");
    }

    private List<String> readBranches(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read allowed branches", exception);
        }
    }

    private EligibilityResponse.EligibilityCheck check(String label, boolean passed, String detail) {
        return new EligibilityResponse.EligibilityCheck(label, passed, detail);
    }
}
