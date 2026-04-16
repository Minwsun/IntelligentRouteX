package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickupAnchorSelectorTest {

    @Test
    void selectsDeterministicAnchorsAndRespectsMaxAnchors() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getCandidate().setMaxAnchors(2);
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        PickupAnchorSelector selector = new PickupAnchorSelector(properties);

        List<PickupAnchor> first = selector.select(
                RouteTestFixtures.bundleStage(properties, RouteTestFixtures.pairClusterStage(properties)).bundleCandidates(),
                context);
        List<PickupAnchor> second = selector.select(
                RouteTestFixtures.bundleStage(properties, RouteTestFixtures.pairClusterStage(properties)).bundleCandidates(),
                context);

        assertEquals(first, second);
        assertTrue(first.stream().collect(java.util.stream.Collectors.groupingBy(PickupAnchor::bundleId)).values().stream()
                .allMatch(anchors -> anchors.size() <= 2));
    }

    @Test
    void urgentBundleCanPrioritizeUrgentAnchor() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        PickupAnchorSelector selector = new PickupAnchorSelector(properties);

        List<PickupAnchor> anchors = selector.select(
                RouteTestFixtures.bundleStage(properties, RouteTestFixtures.pairClusterStage(properties)).bundleCandidates(),
                context);

        assertTrue(anchors.stream().anyMatch(anchor -> context.order(anchor.anchorOrderId()).urgent()));
    }
}
