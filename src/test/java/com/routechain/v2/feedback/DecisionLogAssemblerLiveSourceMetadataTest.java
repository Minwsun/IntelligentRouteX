package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionLogAssemblerLiveSourceMetadataTest {

    @Test
    void logsLiveSourceMetadataWithoutRecomputation() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);

        DispatchV2Result result = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestOpenMeteoClient.freshHeavyRain(),
                TestTomTomTrafficRefineClient.applied(1.15, true))
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        DecisionLogRecord record = new DecisionLogAssembler().assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), result);

        assertTrue(record.liveStageMetadata().stream().anyMatch(metadata -> metadata.sourceName().equals("open-meteo")));
        assertTrue(record.liveStageMetadata().stream().anyMatch(metadata -> metadata.sourceName().equals("tomtom-traffic")));
    }
}
