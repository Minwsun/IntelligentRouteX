package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleScorerTest {

    @Test
    void deterministicScoreIsStableAndUrgencyGetsLift() {
        BundleContext context = new BundleContext(BundleTestFixtures.window().orders(), BundleTestFixtures.graph(), List.of());
        BundleScorer scorer = new BundleScorer(RouteChainDispatchV2Properties.defaults());
        BundleCandidate urgent = new BundleCandidate("bundle-candidate/v1", "u", BundleFamily.URGENT_COMPANION, List.of("order-1", "order-3"), "order-1|order-3", "order-1", "0:0", 0.0, true, List.of());
        BundleCandidate compact = new BundleCandidate("bundle-candidate/v1", "c", BundleFamily.COMPACT_CLIQUE, List.of("order-1", "order-3"), "order-1|order-3", "order-1", "0:0", 0.0, true, List.of());

        BundleCandidate scoredUrgent = scorer.score(urgent, context);
        BundleCandidate scoredUrgentAgain = scorer.score(urgent, context);
        BundleCandidate scoredCompact = scorer.score(compact, context);

        assertEquals(scoredUrgent.score(), scoredUrgentAgain.score());
        assertTrue(scoredUrgent.score() > scoredCompact.score());
    }
}
