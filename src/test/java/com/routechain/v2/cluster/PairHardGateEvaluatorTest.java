package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairHardGateEvaluatorTest {

    @Test
    void compactPairPasses() {
        PairHardGateEvaluator evaluator = new PairHardGateEvaluator(RouteChainDispatchV2Properties.defaults());

        PairGateDecision decision = evaluator.evaluate(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                1.0,
                4.0,
                5L,
                20.0,
                true,
                1.05,
                0.9,
                false));

        assertTrue(decision.passed());
    }

    @Test
    void farPickupFails() {
        PairHardGateEvaluator evaluator = new PairHardGateEvaluator(RouteChainDispatchV2Properties.defaults());

        PairGateDecision decision = evaluator.evaluate(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                3.5,
                12.0,
                5L,
                20.0,
                false,
                1.05,
                0.4,
                false));

        assertFalse(decision.passed());
        assertTrue(decision.reasons().contains("pickup-distance-too-far"));
    }

    @Test
    void largeReadyGapFails() {
        PairHardGateEvaluator evaluator = new PairHardGateEvaluator(RouteChainDispatchV2Properties.defaults());

        PairGateDecision decision = evaluator.evaluate(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                1.0,
                4.0,
                20L,
                20.0,
                true,
                1.05,
                0.9,
                false));

        assertFalse(decision.passed());
        assertTrue(decision.reasons().contains("ready-gap-too-large"));
    }

    @Test
    void weatherTightenedGateRejectsMoreAggressively() {
        PairHardGateEvaluator evaluator = new PairHardGateEvaluator(RouteChainDispatchV2Properties.defaults());

        PairGateDecision clearDecision = evaluator.evaluate(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                1.8,
                4.0,
                12L,
                20.0,
                true,
                1.10,
                0.9,
                false));
        PairGateDecision weatherDecision = evaluator.evaluate(new PairFeatureVector(
                "pair-feature-vector/v1",
                "order-1",
                "order-2",
                1.8,
                4.0,
                12L,
                20.0,
                true,
                1.10,
                0.9,
                true));

        assertTrue(clearDecision.passed());
        assertFalse(weatherDecision.passed());
    }
}
