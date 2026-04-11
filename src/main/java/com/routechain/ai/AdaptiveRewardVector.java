package com.routechain.ai;

/**
 * Reward vector keeps courier, customer, and marketplace utility separate.
 */
public record AdaptiveRewardVector(
        double courierUtility,
        double customerUtility,
        double marketplaceUtility
) {
    public double combinedReward() {
        return clamp01(courierUtility) * 0.34
                + clamp01(customerUtility) * 0.33
                + clamp01(marketplaceUtility) * 0.33;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
