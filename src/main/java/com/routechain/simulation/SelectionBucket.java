package com.routechain.simulation;

/**
 * Selection buckets used by the execution-first solver path.
 */
public enum SelectionBucket {
    WAVE_LOCAL,
    EXTENSION_LOCAL,
    HOLD_WAIT3,
    FALLBACK_LOCAL_LOW_DEADHEAD,
    BORROWED_COVERAGE,
    EMERGENCY_COVERAGE
}
