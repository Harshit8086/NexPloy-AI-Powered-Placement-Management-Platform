package com.nexploy.campuslink.matching.dto;

/**
 * Officer decision over a scored match: {@code decision} is "SHORTLIST" or "REJECT",
 * with an optional free-text reason.
 */
public record ShortlistRequest(String decision, String reason) {
}
