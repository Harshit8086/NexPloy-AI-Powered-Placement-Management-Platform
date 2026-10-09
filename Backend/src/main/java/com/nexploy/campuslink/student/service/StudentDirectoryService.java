package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.student.dto.CsvImportResult;
import com.nexploy.campuslink.student.dto.StudentDirectoryCreateRequest;
import com.nexploy.campuslink.student.dto.StudentDirectoryUpdateRequest;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.DirectoryRecordLinkedException;
import com.nexploy.campuslink.student.exception.StudentAlreadyExistsException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The officer-managed central student directory (V60). Records are created by the officer (CSV
 * import or manual entry), live in the SAME students table with {@code source=DIRECTORY}, and
 * carry no account until the student registers and claims the row. Deactivation is soft
 * ({@code active=false}) so child tables (applications, offers, skills) keep their FK targets.
 */
@Service
@RequiredArgsConstructor
public class StudentDirectoryService {

    private final StudentRepository studentRepository;
    private final CampusRepository campusRepository;
    private final UserAccountRepository userAccountRepository;

    /**
     * Creates one directory record. Pre-checks mirror registration: the campus must be active and
     * the (campus, email) identity must be free — the V47 functional unique index remains the
     * race-condition backstop.
     */
    @Transactional
    public Student create(StudentDirectoryCreateRequest request) {
        requireActiveCampus(request.campusId());
        if (studentRepository.findByCampusIdAndEmailIgnoreCase(request.campusId(), request.email()).isPresent()) {
            throw new StudentAlreadyExistsException(request.email());
        }
        Student student = Student.builder()
            .id(newStudentId())
            .campusId(request.campusId())
            .name(request.name())
            .email(request.email())
            .rollNumber(blankFallback(request.rollNumber(), null))
            .branch(request.branch())
            .branchCode(request.branchCode())
            .department(request.department())
            .batch(request.batch())
            .semester(request.semester())
            .graduationYear(request.graduationYear())
            .yearLabel(request.yearLabel())
            .cgpa(request.cgpa())
            .tenthPercentage(request.tenthPercentage())
            .twelfthPercentage(request.twelfthPercentage())
            .backlogs(request.backlogs())
            .phone(request.phone())
            .source("DIRECTORY")
            .active(true)
            .readinessScore(0)
            .placementStatus("UNPLACED")
            .build();
        return studentRepository.save(student);
    }

    /**
     * Edits directory-owned identity/academic fields. Email is immutable once the record is
     * claimed (an account with that email exists) — renaming it would break the username link.
     * An unclaimed record's email may change after a duplicate re-check.
     */
    @Transactional
    public Student update(Student student, StudentDirectoryUpdateRequest request) {
        if (request.name() != null) student.setName(request.name());
        if (request.rollNumber() != null) student.setRollNumber(request.rollNumber());
        if (request.branch() != null) student.setBranch(request.branch());
        if (request.branchCode() != null) student.setBranchCode(request.branchCode());
        if (request.department() != null) student.setDepartment(request.department());
        if (request.batch() != null) student.setBatch(request.batch());
        if (request.semester() != null) student.setSemester(request.semester());
        if (request.graduationYear() != null) student.setGraduationYear(request.graduationYear());
        if (request.yearLabel() != null) student.setYearLabel(request.yearLabel());
        if (request.cgpa() != null) student.setCgpa(request.cgpa());
        if (request.tenthPercentage() != null) student.setTenthPercentage(request.tenthPercentage());
        if (request.twelfthPercentage() != null) student.setTwelfthPercentage(request.twelfthPercentage());
        if (request.backlogs() != null) student.setBacklogs(request.backlogs());
        if (request.phone() != null) student.setPhone(request.phone());
        if (request.email() != null && !request.email().equalsIgnoreCase(student.getEmail())) {
            if (userAccountRepository.existsByUsernameIgnoreCase(student.getEmail())) {
                throw new DirectoryRecordLinkedException(student.getId());
            }
            studentRepository.findByCampusIdAndEmailIgnoreCase(student.getCampusId(), request.email())
                .ifPresent(existing -> {
                    throw new StudentAlreadyExistsException(request.email());
                });
            student.setEmail(request.email());
        }
        return studentRepository.save(student);
    }

    /**
     * Soft-active toggle. Deactivation also disables the linked account (if the student had
     * claimed the row), so Spring Security rejects their login with 401; reactivation restores
     * both. The student row itself is never hard-deleted.
     */
    @Transactional
    public Student setActive(Student student, boolean active) {
        student.setActive(active);
        userAccountRepository.findById(student.getEmail()).ifPresent(account -> {
            account.setEnabled(active);
            userAccountRepository.save(account);
        });
        return studentRepository.save(student);
    }

    /** CSV header contract; columns beyond these are ignored. */
    private static final List<String> CSV_COLUMNS = List.of(
        "rollNumber", "email", "name", "branch", "branchCode", "department", "batch",
        "semester", "graduationYear", "yearLabel", "cgpa", "backlogs",
        "tenthPercentage", "twelfthPercentage", "phone");

    /**
     * Bulk directory import from an officer-uploaded CSV. Every row is validated independently; a
     * bad or duplicate row is skipped with a reason and never aborts the rest. Imported rows are
     * active DIRECTORY records with no account.
     */
    @Transactional
    public CsvImportResult importCsv(String campusId, MultipartFile file) {
        requireActiveCampus(campusId);
        List<CsvImportResult.RowError> errors = new ArrayList<>();
        int imported = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .build();
        try (Reader reader = new InputStreamReader(withoutBom(file.getInputStream()), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            for (CSVRecord record : parser) {
                int rowNumber = (int) record.getRecordNumber();
                try {
                    if (importRow(campusId, record)) {
                        imported++;
                    } else {
                        errors.add(new CsvImportResult.RowError(rowNumber,
                            "Email already exists in this campus directory."));
                    }
                } catch (IllegalArgumentException exception) {
                    errors.add(new CsvImportResult.RowError(rowNumber, exception.getMessage()));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the uploaded CSV", exception);
        }
        return new CsvImportResult(imported, errors.size(), errors);
    }

    /**
     * Validates and persists one CSV row. Returns {@code false} when the (campus, email) identity
     * already exists — a skip, not an abort.
     */
    private boolean importRow(String campusId, CSVRecord record) {
        String rollNumber = column(record, "rollNumber");
        String email = column(record, "email");
        String name = column(record, "name");
        if (rollNumber.isBlank()) throw new IllegalArgumentException("rollNumber is required");
        if (email.isBlank()) throw new IllegalArgumentException("email is required");
        if (name.isBlank()) throw new IllegalArgumentException("name is required");

        BigDecimal cgpa = decimal(column(record, "cgpa"), "cgpa", BigDecimal.ZERO, BigDecimal.TEN);
        BigDecimal tenth = decimal(column(record, "tenthPercentage"), "tenthPercentage",
            BigDecimal.ZERO, new BigDecimal("100"));
        BigDecimal twelfth = decimal(column(record, "twelfthPercentage"), "twelfthPercentage",
            BigDecimal.ZERO, new BigDecimal("100"));
        Integer backlogs = integer(column(record, "backlogs"), "backlogs", 0, Integer.MAX_VALUE);
        Integer semester = integer(column(record, "semester"), "semester", 1, 12);
        Integer graduationYear = integer(column(record, "graduationYear"), "graduationYear", 1900, 2100);

        if (studentRepository.findByCampusIdAndEmailIgnoreCase(campusId, email).isPresent()) {
            return false;
        }
        studentRepository.save(Student.builder()
            .id(newStudentId())
            .campusId(campusId)
            .name(name)
            .email(email)
            .rollNumber(rollNumber)
            .branch(column(record, "branch"))
            .branchCode(column(record, "branchCode"))
            .department(column(record, "department"))
            .batch(column(record, "batch"))
            .semester(semester)
            .graduationYear(graduationYear)
            .yearLabel(column(record, "yearLabel"))
            .cgpa(cgpa)
            .tenthPercentage(tenth)
            .twelfthPercentage(twelfth)
            .backlogs(backlogs)
            .phone(column(record, "phone"))
            .source("DIRECTORY")
            .active(true)
            .readinessScore(0)
            .placementStatus("UNPLACED")
            .build());
        return true;
    }

    /**
     * Excel-saved CSVs carry a UTF-8 byte-order mark; left in place it fuses with the first header
     * ("rollNumber"), so no row maps it and every row fails as missing rollNumber.
     */
    private InputStream withoutBom(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(3);
        byte[] bom = in.readNBytes(3);
        boolean isBom = bom.length == 3 && (bom[0] & 0xFF) == 0xEF
            && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF;
        if (!isBom) {
            in.reset();
        }
        return in;
    }

    /** Reads an optional column by name; absent header or blank value yields "". */
    private String column(CSVRecord record, String name) {
        if (!CSV_COLUMNS.contains(name) || !record.isMapped(name)) {
            return "";
        }
        String value = record.get(name);
        return value == null ? "" : value.trim();
    }

    private BigDecimal decimal(String raw, String label, BigDecimal min, BigDecimal max) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
                throw new IllegalArgumentException(label + " out of range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " is not a number");
        }
    }

    private Integer integer(String raw, String label, int min, int max) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + " out of range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " is not a number");
        }
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private void requireActiveCampus(String campusId) {
        Campus campus = campusRepository.findById(campusId)
            .orElseThrow(() -> new IllegalArgumentException("Campus not found: " + campusId));
        if (!campus.isActive()) {
            throw new IllegalArgumentException("Campus is not active: " + campusId);
        }
    }

    private String newStudentId() {
        return "stu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
