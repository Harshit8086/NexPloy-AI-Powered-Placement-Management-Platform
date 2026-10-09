package com.nexploy.campuslink.drive.dto;

import java.util.List;

public record EligibilityResponse(
    String studentId,
    String driveId,
    boolean eligible,
    List<EligibilityCheck> checks,
    List<String> failureReasons
) {

    public record EligibilityCheck(String label, boolean passed, String detail) {
    }
}

