package com.nexploy.campuslink.schedule.exception;

public class StaleOptimizationRunException extends RuntimeException {

    public StaleOptimizationRunException() {
        super("Optimization run is stale because the schedule changed");
    }
}
