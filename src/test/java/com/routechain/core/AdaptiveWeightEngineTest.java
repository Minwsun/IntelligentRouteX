package com.routechain.core;

import com.routechain.domain.Enums.WeatherProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveWeightEngineTest {

    @Test
    void positiveOutcomeShouldLiftScoreForSimilarPlan() {
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();
        CompactDispatchContext context = new CompactDispatchContext(
                java.util.List.of(),
                12,
                0.22,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-11T05:00:00Z"),
                6,
                4);
        PlanFeatureVector phi = new PlanFeatureVector(0.82, 0.18, 0.72, 0.64, 0.68, 0.70, 0.20, 0.10);

        double before = engine.score(phi, context);
        engine.recordOutcome(phi, context, new OutcomeVector(0.95, 1.0, 0.90, 0.82, 0.84, 0.88, 0.96));
        double after = engine.score(phi, context);

        assertTrue(after > before, "A strong realized outcome should increase the score for a similar compact plan");
    }

    @Test
    void restoreShouldRecoverPreviousSnapshot() {
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();
        CompactDispatchContext context = new CompactDispatchContext(
                java.util.List.of(),
                19,
                0.30,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-11T12:00:00Z"),
                8,
                5);
        PlanFeatureVector phi = new PlanFeatureVector(0.78, 0.22, 0.60, 0.58, 0.62, 0.66, 0.28, 0.14);

        WeightSnapshot snapshot = engine.snapshot();
        double baseline = engine.score(phi, context);

        engine.recordOutcome(phi, context, new OutcomeVector(0.90, 1.0, 0.84, 0.70, 0.76, 0.80, 0.92));
        engine.restore(snapshot);

        assertEquals(baseline, engine.score(phi, context), 1e-9,
                "Restoring a snapshot should recover the original compact scoring behavior");
    }
}
