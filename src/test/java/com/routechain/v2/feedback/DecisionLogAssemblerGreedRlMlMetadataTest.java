package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionLogAssemblerGreedRlMlMetadataTest {

    @Test
    void logsGreedRlMetadataWithoutRecomputation() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);
        DispatchV2Result result = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                TestGreedRlClient.applied())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        DecisionLogRecord record = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), result);

        assertTrue(record.mlStageMetadata().stream().anyMatch(metadata -> metadata.sourceModel().equals("greedrl-local")));
    }
}
