package com.routechain.ai;

/**
 * Internal dispatch regime for stress-aware tuning.
 */
public enum StressRegime {
    NORMAL,
    STRESS,
    SEVERE_STRESS;

    public boolean isAtLeast(StressRegime other) {
        return this.ordinal() >= other.ordinal();
    }

    public static StressRegime max(StressRegime a, StressRegime b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
