package com.routechain.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardProjectionTest {

    @Test
    void projectedRewardShouldStayWithinUnitIntervalAndIncreaseWithBetterBreakdown() {
        PlanFeatureVector phi = new PlanFeatureVector(0.84, 0.16, 0.74, 0.62, 0.68, 0.72, 0.18, 0.08);
        AdaptiveScoreBreakdown weak = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                0.24,
                0.12,
                0.12,
                Map.of("on_time_probability", 0.12),
                Map.of("lambda_late", 0.10));
        AdaptiveScoreBreakdown strong = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                0.62,
                0.08,
                0.54,
                Map.of("on_time_probability", 0.32),
                Map.of("lambda_late", 0.08));

        double weakReward = RewardProjection.project(weak, phi);
        double strongReward = RewardProjection.project(strong, phi);

        assertTrue(weakReward >= 0.0 && weakReward <= 1.0);
        assertTrue(strongReward >= 0.0 && strongReward <= 1.0);
        assertTrue(strongReward > weakReward);
    }
}
