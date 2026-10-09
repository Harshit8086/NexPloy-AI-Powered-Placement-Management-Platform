package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.student.dto.StudentProject;
import com.nexploy.campuslink.student.dto.StudentUpdateRequest;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentProjectEntry;
import com.nexploy.campuslink.student.repository.StudentProjectEntryRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the normalized project table (V40) in sync with a student profile edit. This is the
 * write half of the dual-write: {@link StudentService#applyUpdate} still serializes into the
 * retained JSON columns, and this service mirrors the same values into the normalized rows.
 * Only the fields actually present in the patch are touched — a null list leaves the existing
 * rows untouched, matching the JSON path's partial-patch semantics.
 *
 * <p>Kept separate from {@link StudentService} so that class stays a pure, repository-free
 * mapper (constructed directly in unit tests). Reads still come from the JSON columns, so a
 * failure to normalize can never regress a read path.
 */
@Service
@RequiredArgsConstructor
public class StudentProfileNormalizationService {

    private final StudentProjectEntryRepository projectRepository;
    private final ObjectMapper objectMapper;

    /**
     * Mirrors the patch's projects into the normalized table. Each provided list fully
     * replaces the student's existing rows (delete-by-student then re-insert), so the
     * normalized side matches the JSON column exactly. The delete clears any prior rows
     * regardless of how their ids were generated (the V40 backfill uses md5-based ids; edits
     * use {@code stuproj_} + student id + ordinal), so the two never collide or leave orphans.
     */
    @Transactional
    public void sync(Student student, StudentUpdateRequest request) {
        if (request.projects() != null) {
            projectRepository.deleteByStudentId(student.getId());
            List<StudentProjectEntry> rows = new ArrayList<>();
            List<StudentProject> projects = request.projects();
            for (int i = 0; i < projects.size(); i++) {
                StudentProject project = projects.get(i);
                if (project == null) {
                    continue;
                }
                rows.add(StudentProjectEntry.builder()
                    .id("stuproj_" + student.getId() + "_" + i)
                    .studentId(student.getId())
                    .campusId(student.getCampusId())
                    .name(project.name())
                    .summary(project.summary())
                    .stackJson(writeStack(project.stack()))
                    .githubUrl(project.githubUrl())
                    .liveUrl(project.liveUrl())
                    .sortOrder(i)
                    .build());
            }
            projectRepository.saveAll(rows);
        }
    }

    private String writeStack(List<String> stack) {
        if (stack == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(stack);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not write project stack", exception);
        }
    }
}
