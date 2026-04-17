package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairSimilarityScorerTest {

    @Test
    void hardGateFailureProducesZeroScore() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        PairSimilarityScorer scorer = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                new NoOpTabularScoringClient());

        PairCompatibility compatibility = scorer.score(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                5.0,
                12.0,
                30L,
                80.0,
                false,
                1.5,
                0.1,
                false));

        assertFalse(compatibility.hardGatePassed());
        assertEquals(0.0, compatibility.score());
    }

    @Test
    void compatiblePairGetsPositiveDeterministicScore() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        PairSimilarityScorer scorer = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                new NoOpTabularScoringClient());

        PairCompatibility compatibility = scorer.score(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                0.8,
                3.0,
                4L,
                12.0,
                true,
                1.05,
                0.95,
                false));

        assertTrue(compatibility.hardGatePassed());
        assertTrue(compatibility.score() > 0.0);
    }

    @Test
    void mlEnabledWithoutWorkerKeepsDeterministicScoreAndAddsDegradeReason() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        PairSimilarityScorer scorer = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                new NoOpTabularScoringClient());

        PairCompatibility compatibility = scorer.score(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                0.8,
                3.0,
                4L,
                12.0,
                true,
                1.05,
                0.95,
                false));

        assertTrue(compatibility.hardGatePassed());
        assertTrue(compatibility.score() > 0.0);
        assertTrue(compatibility.degradeReasons().contains("pair-ml-unavailable"));
    }
}
