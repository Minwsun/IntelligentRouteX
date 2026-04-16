package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BoundaryCandidateSelectorTest {

    @Test
    void selectsOnlyOrdersWithPairSupportIntoCluster() {
        BoundaryCandidateSelector selector = new BoundaryCandidateSelector(RouteChainDispatchV2Properties.defaults());

        Map<String, List<BoundaryCandidate>> candidates = selector.select(
                BundleTestFixtures.window(),
                BundleTestFixtures.microClusters(),
                BundleTestFixtures.graph());

        assertEquals(List.of("order-3", "order-4"), candidates.get("cluster-001").stream().map(BoundaryCandidate::orderId).toList());
        assertFalse(candidates.get("cluster-002").isEmpty());
    }
}
