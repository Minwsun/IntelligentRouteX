package com.routechain.ai;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.SelectionBucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineWeightUpdateEngineTest {

    @Test
    void shouldIncreaseScoreAfterPositiveOutcomeForStrongBatchState() {
        OnlineWeightUpdateEngine engine = new OnlineWeightUpdateEngine();
        GraphRouteState state = sampleState();
        BayesianOutcomeEstimate futureEstimate = new BayesianOutcomeEstimate(0.72, 2.1, 0.8, 0.40);
        BayesianRiskEstimate riskEstimate = new BayesianRiskEstimate(0.12, 0.05, 0.32);
        RetrievedRouteAnalogs analogs = new RetrievedRouteAnalogs(0.66, 0.44, 0.02, 3);
        ShadowOracleScore oracleScore = new ShadowOracleScore(0.74, 0.68, 0.01, "shadow");

        double before = engine.score(state, futureEstimate, riskEstimate, analogs, oracleScore);

        for (int i = 0; i < 12; i++) {
            engine.update(
                    state,
                    new AdaptiveRewardVector(0.84, 0.78, 0.82),
                    futureEstimate,
                    riskEstimate);
        }

        double after = engine.score(state, futureEstimate, riskEstimate, analogs, oracleScore);
        assertTrue(after > before,
                "Positive outcome feedback should lift the adaptive score for a similar route state");
    }

    private static GraphRouteState sampleState() {
        return new GraphRouteState(
                "zone-a",
                "instant",
                SelectionBucket.WAVE_LOCAL,
                WeatherProfile.CLEAR,
                12,
                2,
                0.22,
                0.78,
                0.72,
                0.14,
                0.20,
                0.76,
                0.68,
                0.12,
                0.64,
                0.70,
                2.0,
                0.8,
                0.10,
                0.14);
    }
}
