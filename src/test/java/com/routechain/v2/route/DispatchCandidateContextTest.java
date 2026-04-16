package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCandidateContextTest {

    @Test
    void exposesBundleBoundaryAndPairSupportInOnePlace() {
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(RouteChainDispatchV2Properties.defaults());
        String bundleId = context.bundleIds().getFirst();

        assertNotNull(context.bundle(bundleId));
        assertNotNull(context.clusterForBundle(bundleId));
        assertEquals(context.bundle(bundleId).clusterId(), context.clusterForBundle(bundleId).clusterId());
        assertTrue(context.averagePairSupport(context.bundle(bundleId).orderIds()) >= 0.0);
    }
}
