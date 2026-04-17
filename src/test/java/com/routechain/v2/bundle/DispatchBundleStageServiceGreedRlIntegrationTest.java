package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestGreedRlClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchBundleStageServiceGreedRlIntegrationTest {

    @Test
    void workerUpAddsGreedRlCandidatesAndMetadata() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);

        DispatchBundleStage stage = RouteChainBundleFixtures.bundleStage(properties, TestGreedRlClient.applied());

        assertTrue(stage.bundlePoolSummary().sourceCounts().getOrDefault(BundleProposalSource.GREEDRL_PROPOSAL, 0) > 0);
        assertTrue(stage.mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("greedrl-local")));
    }

    @Test
    void workerDownPreservesDeterministicBundlePool() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);

        DispatchBundleStage stage = RouteChainBundleFixtures.bundleStage(properties, TestGreedRlClient.notApplied("greedrl-unavailable"));

        assertFalse(stage.bundleCandidates().isEmpty());
        assertTrue(stage.bundleCandidates().stream().noneMatch(candidate -> candidate.proposalSource() == BundleProposalSource.GREEDRL_PROPOSAL));
        assertTrue(stage.degradeReasons().contains("greedrl-ml-unavailable"));
    }

    @Test
    void scopeGuardBlocksOversizedRequestsAndAddsExplicitReason() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);
        properties.getMl().getGreedrl().setMaxOrdersPerRequest(1);

        DispatchBundleStage stage = RouteChainBundleFixtures.bundleStage(properties, TestGreedRlClient.applied());

        assertTrue(stage.degradeReasons().contains("greedrl-scope-too-large"));
        assertTrue(stage.bundleCandidates().stream().noneMatch(candidate -> candidate.proposalSource() == BundleProposalSource.GREEDRL_PROPOSAL));
    }
}
