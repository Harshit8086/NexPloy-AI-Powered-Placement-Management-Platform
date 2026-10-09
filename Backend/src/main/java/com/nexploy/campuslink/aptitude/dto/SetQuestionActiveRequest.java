package com.nexploy.campuslink.aptitude.dto;

/** Body of PATCH /api/aptitude/bank/{id}/active — soft activate/deactivate of a bank question. */
public record SetQuestionActiveRequest(
    boolean active
) {
}
