package com.nexploy.campuslink.recommendation.dto;

/**
 * The recommendation state for a candidate on a drive, derived deterministically and NEVER by the LLM.
 * Kept separate from the official {@code ApplicationStatus} funnel — this classifies the recommendation
 * only and does not move an application.
 *
 * <ul>
 *   <li>{@code NOT_ELIGIBLE} — the deterministic eligibility gate failed (hard criteria).</li>
 *   <li>{@code ELIGIBLE} — eligible, no officer shortlist decision recorded yet.</li>
 *   <li>{@code SHORTLISTED} — eligible and the latest officer decision was SHORTLIST.</li>
 *   <li>{@code NOT_SHORTLISTED} — eligible and the latest officer decision was REJECT.</li>
 * </ul>
 */
public enum RecommendationType {
    NOT_ELIGIBLE,
    ELIGIBLE,
    SHORTLISTED,
    NOT_SHORTLISTED
}
