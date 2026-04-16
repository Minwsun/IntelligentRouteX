package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.bundle.BundleCandidate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleProvenancePropagationTest {

    @Test
    void routeStagesCanReadClusterAndBoundaryProvenanceDirectlyFromBundleData() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);

        BundleCandidate bundle = bundleStage.bundleCandidates().getFirst();

        assertNotNull(bundle.clusterId());
        assertTrue(pairClusterStage.microClusters().stream().anyMatch(cluster -> cluster.clusterId().equals(bundle.clusterId())));
        bundleStage.bundleCandidates().stream()
                .filter(BundleCandidate::boundaryCross)
                .forEach(boundaryCrossBundle -> assertFalse(boundaryCrossBundle.acceptedBoundaryOrderIds().isEmpty()));
    }
}
