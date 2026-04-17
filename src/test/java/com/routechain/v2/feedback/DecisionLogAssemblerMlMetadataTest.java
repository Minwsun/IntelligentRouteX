package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionLogAssemblerMlMetadataTest {

    @Test
    void logsMlMetadataWithoutRecomputing() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        DispatchV2Result result = TestDispatchV2Factory.core(properties, TestTabularScoringClient.applied(0.05))
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        DecisionLogRecord record = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), result);

        assertFalse(record.mlStageMetadata().isEmpty());
        assertTrue(record.mlStageMetadata().stream().allMatch(metadata -> metadata.applied() || metadata.fallbackUsed()));
    }
}
