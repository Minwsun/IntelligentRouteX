package com.routechain.ai;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.SelectionBucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedLinearBanditEngineTest {

    @Test
    void shouldIncreaseScoreAfterResolvedPositiveOutcome() {
        DelayedLinearBanditEngine engine = new DelayedLinearBanditEngine();
        GraphRouteState state = sampleState();
        BayesianOutcomeEstimate futureEstimate = new BayesianOutcomeEstimate(0.72, 2.0, 0.8, 0.40);
        BayesianRiskEstimate riskEstimate = new BayesianRiskEstimate(0.10, 0.05, 0.30);
        RetrievedRouteAnalogs analogs = new RetrievedRouteAnalogs(0.66, 0.44, 0.02, 4);
        OracleDisagreementSignal disagreement = new OracleDisagreementSignal(0.74, 0.69, 0.02, "shadow", false);
        PseudoRewardEnvelope pseudoRewardEnvelope = new PseudoRewardEnvelope(0.74, 0.66, 0.72, 0.09, 0.02);

        double before = engine.score(
                state,
                futureEstimate,
                riskEstimate,
                analogs,
                disagreement,
                pseudoRewardEnvelope,
                true);

        engine.registerDecision("trace-1", 100L, state, before, pseudoRewardEnvelope, disagreement);
        engine.resolveDecision("trace-1", 104L, new AdaptiveRewardVector(0.84, 0.80, 0.82), true);

        double after = engine.score(
                state,
                futureEstimate,
                riskEstimate,
                analogs,
                disagreement,
                pseudoRewardEnvelope,
                true);

        assertTrue(after > before,
                "Delayed bandit should lift the score of a similar route after a strong realized outcome");
    }

    @Test
    void shouldRestoreCheckpointSnapshot() {
        DelayedLinearBanditEngine engine = new DelayedLinearBanditEngine();
        BanditPosteriorSnapshot snapshot = engine.snapshot();
        GraphRouteState state = sampleState();
        PseudoRewardEnvelope pseudoRewardEnvelope = new PseudoRewardEnvelope(0.70, 0.62, 0.68, 0.10, 0.03);
        OracleDisagreementSignal disagreement = new OracleDisagreementSignal(0.70, 0.66, 0.03, "shadow", false);

        double baseline = engine.score(
                state,
                new BayesianOutcomeEstimate(0.68, 2.2, 0.9, 0.40),
                new BayesianRiskEstimate(0.12, 0.06, 0.30),
                new RetrievedRouteAnalogs(0.62, 0.40, 0.02, 3),
                disagreement,
                pseudoRewardEnvelope,
                true);

        engine.registerDecision("trace-restore", 10L, state, baseline, pseudoRewardEnvelope, disagreement);
        engine.resolveDecision("trace-restore", 11L, new AdaptiveRewardVector(0.90, 0.88, 0.86), true);
        engine.restore(snapshot);

        double restored = engine.score(
                state,
                new BayesianOutcomeEstimate(0.68, 2.2, 0.9, 0.40),
                new BayesianRiskEstimate(0.12, 0.06, 0.30),
                new RetrievedRouteAnalogs(0.62, 0.40, 0.02, 3),
                disagreement,
                pseudoRewardEnvelope,
                true);
        assertEquals(baseline, restored, 1e-9,
                "Restoring a snapshot should recover the exact prior scoring behavior");
    }

    private static GraphRouteState sampleState() {
        return new GraphRouteState(
                "zone-bandit",
                "instant",
                SelectionBucket.WAVE_LOCAL,
                WeatherProfile.CLEAR,
                12,
                2,
                0.22,
                0.78,
                0.70,
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
