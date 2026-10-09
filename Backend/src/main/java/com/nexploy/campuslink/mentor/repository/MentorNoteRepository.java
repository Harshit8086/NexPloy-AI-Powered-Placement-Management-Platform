package com.nexploy.campuslink.mentor.repository;

import com.nexploy.campuslink.mentor.entity.MentorNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorNoteRepository extends JpaRepository<MentorNote, String> {

    List<MentorNote> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
