package com.routechain.ai;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.SelectionBucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BayesianContinuationEstimatorTest {

    @Test
    void shouldRaiseConfidenceAndImproveHitEstimateAfterPositiveSamples() {
        BayesianContinuationEstimator estimator = new BayesianContinuationEstimator();
        GraphRouteState state = sampleState();

        BayesianOutcomeEstimate before = estimator.estimate(state);
        for (int i = 0; i < 20; i++) {
            estimator.update(state, true, 1.8, 0.7);
        }

        BayesianOutcomeEstimate after = estimator.estimate(state);
        assertTrue(after.confidence() > before.confidence(),
                "Confidence should increase after repeated matching outcomes");
        assertTrue(after.postDropHitProbability() > before.postDropHitProbability(),
                "Posterior hit probability should move toward the observed positive continuation pattern");
    }

    private static GraphRouteState sampleState() {
        return new GraphRouteState(
                "zone-b",
                "instant",
                SelectionBucket.EXTENSION_LOCAL,
                WeatherProfile.LIGHT_RAIN,
                18,
                2,
                0.28,
                0.70,
                0.64,
                0.18,
                0.24,
                0.62,
                0.54,
                0.18,
                0.58,
                0.56,
                3.6,
                1.1,
                0.14,
                0.22);
    }
}
