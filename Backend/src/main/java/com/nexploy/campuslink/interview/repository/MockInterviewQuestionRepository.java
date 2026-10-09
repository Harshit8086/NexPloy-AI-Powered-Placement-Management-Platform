package com.nexploy.campuslink.interview.repository;

import com.nexploy.campuslink.interview.entity.MockInterviewQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockInterviewQuestionRepository extends JpaRepository<MockInterviewQuestion, String> {

    /** Questions for one interview in asked order. */
    List<MockInterviewQuestion> findByMockInterviewIdOrderByOrdinalAsc(String mockInterviewId);
}
