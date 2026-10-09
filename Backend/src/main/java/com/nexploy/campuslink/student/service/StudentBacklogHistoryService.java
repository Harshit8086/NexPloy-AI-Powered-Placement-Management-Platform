package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentBacklogHistory;
import com.nexploy.campuslink.student.repository.StudentBacklogHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the append-only backlog timeline behind {@code students.backlogs}. Rows are added
 * automatically when the current count changes on a profile edit, or explicitly by an officer as a
 * timeline note. The current count on the student row stays authoritative; this is descriptive
 * history only and never feeds scoring on its own.
 */
@Service
@RequiredArgsConstructor
public class StudentBacklogHistoryService {

    private final StudentBacklogHistoryRepository repository;

    /**
     * Appends a timeline row when the student's active-backlog count actually changed. No-op when the
     * new count is null or equals the previous count. {@code clearedBacklogs} is set only when the
     * count dropped (previous − current), so a rise or a first record leaves it null.
     */
    @Transactional
    public void recordChange(Student student, Integer previousBacklogs, String recordedBy) {
        Integer current = student.getBacklogs();
        if (current == null) {
            return;
        }
        if (previousBacklogs != null && previousBacklogs.equals(current)) {
            return;
        }
        Integer cleared = (previousBacklogs != null && previousBacklogs > current)
            ? previousBacklogs - current
            : null;
        String note = previousBacklogs == null
            ? "Initial backlog record"
            : cleared != null ? cleared + " backlog(s) cleared" : "Backlog count updated";
        repository.save(newEntry(student, student.getSemester(), current, cleared, note, recordedBy));
    }

    /** Officer/admin manual timeline entry; does not alter the student's current count. */
    @Transactional
    public StudentBacklogHistory record(Student student, Integer semester, Integer activeBacklogs,
                                        Integer clearedBacklogs, String note, String recordedBy) {
        return repository.save(newEntry(student, semester, activeBacklogs, clearedBacklogs, note, recordedBy));
    }

    @Transactional(readOnly = true)
    public List<StudentBacklogHistory> list(String studentId) {
        return repository.findByStudentIdOrderByRecordedAtDesc(studentId);
    }

    private StudentBacklogHistory newEntry(Student student, Integer semester, Integer activeBacklogs,
                                           Integer clearedBacklogs, String note, String recordedBy) {
        return StudentBacklogHistory.builder()
            .id("blh_" + UUID.randomUUID())
            .studentId(student.getId())
            .campusId(student.getCampusId())
            .semester(semester)
            .activeBacklogs(activeBacklogs)
            .clearedBacklogs(clearedBacklogs)
            .note(note)
            .recordedBy(recordedBy)
            .recordedAt(OffsetDateTime.now())
            .build();
    }
}
