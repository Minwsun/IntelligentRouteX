package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PairSimilarityScorerMlIntegrationTest {

    @Test
    void workerUpAdjustsScoreAfterGateAndWorkerDownPreservesDeterministicScore() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        PairFeatureVector featureVector = new PairFeatureVector(
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
                false);

        PairCompatibility deterministic = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                TestTabularScoringClient.notApplied("tabular-unavailable"))
                .score(featureVector);
        PairCompatibility adjusted = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                TestTabularScoringClient.applied(0.1))
                .score(featureVector);

        assertTrue(adjusted.score() > deterministic.score());
        assertTrue(deterministic.degradeReasons().contains("pair-ml-unavailable"));
    }
}
