package com.nexploy.campuslink.document.repository;

import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByCampusId(String campusId);

    /** Paged campus-scoped listing for the documents roster. */
    Page<Document> findByCampusId(String campusId, Pageable pageable);

    List<Document> findByStudentId(String studentId);

    /**
     * Documents in a review status uploaded before a cutoff — powers the pending-document reminder
     * sweep. {@code uploadedAt} is used only as a staleness threshold, not as a deadline (documents
     * carry no due date).
     */
    List<Document> findByReviewStatusAndUploadedAtBefore(ReviewStatus reviewStatus, OffsetDateTime cutoff);
}
