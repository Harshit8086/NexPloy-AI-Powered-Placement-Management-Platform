package com.nexploy.campuslink.risk.dto;

/**
 * One action item of an intervention plan (mirrors the frontend `InterventionAction` contract).
 * Stored inside the plan's actions JSON column and returned as-is to the UI.
 */
public record InterventionAction(
    String id,
    String label,
    boolean done
) {
}
