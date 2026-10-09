package com.nexploy.campuslink.analytics.dto;

import java.util.List;

/**
 * Skill supply/demand for a single campus. {@code demand} counts how many currently active drives
 * ask for each skill; {@code supply} pairs that demand with the share of the student cohort that
 * actually claims the skill. Both are live aggregations — an empty campus yields empty lists,
 * never placeholder skills.
 */
public record SkillAnalyticsResponse(List<SkillDemand> demand, List<SkillSupply> supply) {

    /** demandScore = number of active drives requiring this skill. */
    public record SkillDemand(String skill, int demandScore) {}

    /** cohortCoverage = percent of the campus's students who claim this skill (0-100). */
    public record SkillSupply(String skill, int demand, int cohortCoverage) {}
}
