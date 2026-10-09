package com.nexploy.campuslink.offer.dto;

import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record OfferResponse(
    String id,
    String campusId,
    String studentId,
    String driveId,
    String recruiterId,
    String companyName,
    String role,
    BigDecimal packageLpa,
    String offerType,
    String location,
    LocalDate joiningDate,
    LocalDate acceptanceDeadline,
    boolean documentsVerified,
    boolean joined,
    String fromInternship,
    OfferStatus status,
    OffsetDateTime extendedAt,
    OffsetDateTime decidedAt,
    String decidedBy
) {

    public static OfferResponse from(Offer offer) {
        return new OfferResponse(
            offer.getId(),
            offer.getCampusId(),
            offer.getStudentId(),
            offer.getDriveId(),
            offer.getRecruiterId(),
            offer.getCompanyName(),
            offer.getRole(),
            offer.getPackageLpa(),
            offer.getOfferType(),
            offer.getLocation(),
            offer.getJoiningDate(),
            offer.getAcceptanceDeadline(),
            offer.isDocumentsVerified(),
            offer.isJoined(),
            offer.getFromInternship(),
            offer.getStatus(),
            offer.getExtendedAt(),
            offer.getDecidedAt(),
            offer.getDecidedBy()
        );
    }
}
