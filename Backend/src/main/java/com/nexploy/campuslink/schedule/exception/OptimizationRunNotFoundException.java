package com.nexploy.campuslink.schedule.exception;

public class OptimizationRunNotFoundException extends RuntimeException {

    public OptimizationRunNotFoundException(String runId) {
        super("Optimization run not found: " + runId);
    }
}
