package com.nexploy.campuslink.company.dto;

import java.util.List;

/**
 * A company's presence at a campus, aggregated from real data: the identity + company columns come
 * from the recruiter row that represents this company on the campus, and the drive numbers/summaries
 * are computed from that recruiter's own {@code drives}. Nothing is fabricated — a recruiter with no
 * drives yields zero counts and an empty list, and unknown company columns are simply {@code null}.
 *
 * <p>The company {@code id} is the recruiter id: each {@code recruiters} row is one company's presence
 * at one campus, so it is the stable, real key we already have.
 */
public record CompanyResponse(
    String id,
    String campusId,
    String name,
    String industry,
    String website,
    String location,
    int activeDriveCount,
    int totalDriveCount,
    List<CompanyDriveSummary> drives
) {
}
