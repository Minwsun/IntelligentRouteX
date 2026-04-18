package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreTabularOfflineSliceTest {

    @Test
    void localTabularMetadataDoesNotChangeStageCountOrOutputContracts() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);

        DispatchV2Result result = TestDispatchV2Factory.core(properties, TestTabularScoringClient.applied(0.05))
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"), result.decisionStages());
        assertTrue(result.mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("tabular-test")
                && metadata.artifactDigest().equals("sha256:test")));
        assertTrue(result.assignments() != null);
    }
}
