package com.nexploy.campuslink.offer.repository;

import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, String> {

    List<Offer> findByCampusId(String campusId);

    /** Paged campus-scoped listing for the offers roster. */
    Page<Offer> findByCampusId(String campusId, Pageable pageable);

    List<Offer> findByStudentId(String studentId);

    List<Offer> findByRecruiterId(String recruiterId);

    /** Offers in a status with an acceptance deadline in [from, to] — powers the reminder scheduler. */
    List<Offer> findByStatusAndAcceptanceDeadlineBetween(
        OfferStatus status, LocalDate from, LocalDate to);

    /**
     * Offers in a status that were extended before a cutoff — powers the recruiter follow-up sweep.
     * Keyed on {@code extendedAt} age rather than {@code acceptanceDeadline} (which is nullable), so
     * still-EXTENDED offers get chased regardless of whether a deadline was set. An offer leaving
     * EXTENDED (accepted/declined/revoked) drops out automatically.
     */
    List<Offer> findByStatusAndExtendedAtBefore(OfferStatus status, OffsetDateTime cutoff);

    // ---- Set-based analytics aggregates ----

    long countByCampusId(String campusId);

    /** [status, count] per offer status for the campus. */
    @Query("select o.status, count(o) from Offer o where o.campusId = :campusId group by o.status")
    List<Object[]> countByStatusForCampus(@Param("campusId") String campusId);

    /** Distinct students with an ACCEPTED offer in the campus — the deterministic "placed" count. */
    @Query("select count(distinct o.studentId) from Offer o "
        + "where o.campusId = :campusId and o.status = com.nexploy.campuslink.offer.entity.OfferStatus.ACCEPTED")
    long countPlacedStudentsByCampusId(@Param("campusId") String campusId);
}
