package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCandidateContextTest {

    @Test
    void exposesBundleBoundaryAndPairSupportInOnePlace() {
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(RouteChainDispatchV2Properties.defaults());
        String bundleId = context.bundleIds().getFirst();

        assertNotNull(context.bundle(bundleId));
        assertNotNull(context.clusterForBundle(bundleId));
        assertTrue(context.averagePairSupport(context.bundle(bundleId).orderIds()) >= 0.0);
    }
}
