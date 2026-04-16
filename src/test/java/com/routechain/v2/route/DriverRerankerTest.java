package com.routechain.v2.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverRerankerTest {

    @Test
    void reranksDeterministicallyAndPrefersBetterEtaWhenScoresAreClose() {
        DriverReranker reranker = new DriverReranker();
        PickupAnchor anchor = new PickupAnchor("pickup-anchor/v1", "bundle-1", "order-1|order-2", "order-1", 1, 0.7, List.of());

        List<DriverCandidate> candidates = reranker.rerank(anchor, List.of(
                new DriverRouteFeatures("bundle-1", "order-1", "driver-2", 6.0, 0.2, 0.7, 0.7, 0.8, 0.6, 0.0, 0.0, 0.75, List.of(), List.of()),
                new DriverRouteFeatures("bundle-1", "order-1", "driver-1", 5.0, 0.2, 0.7, 0.7, 0.8, 0.6, 0.0, 0.0, 0.75, List.of(), List.of())));

        assertEquals("driver-1", candidates.getFirst().driverId());
        assertEquals(1, candidates.getFirst().rank());
    }
}
