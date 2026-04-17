package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.GreedRlClient;

final class RouteChainBundleFixtures {
    private RouteChainBundleFixtures() {
    }

    static DispatchBundleStage bundleStage(RouteChainDispatchV2Properties properties, GreedRlClient greedRlClient) {
        return new DispatchBundleStageService(
                properties,
                new BoundaryCandidateSelector(properties),
                new BoundaryExpansionEngine(properties),
                new BundleSeedGenerator(properties),
                new BundleFamilyEnumerator(properties),
                new BundleValidator(properties),
                new BundleScorer(properties),
                new BundleDominancePruner(),
                greedRlClient)
                .evaluate(BundleTestFixtures.clearEtaContext(), BundleTestFixtures.pairClusterStage());
    }
}
