package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchEtaContextServiceLiveSourceTest {

    @Test
    void freshnessAndLiveMetadataPropagateIntoEtaStage() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);

        var result = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestOpenMeteoClient.freshHeavyRain(),
                TestTomTomTrafficRefineClient.applied(1.12, true))
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertFalse(result.liveStageMetadata().isEmpty());
        assertTrue(result.freshnessMetadata().weatherFresh());
        assertTrue(result.etaStageTrace().liveWeatherApplied());
    }
}
