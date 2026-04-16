package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.WeatherProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CompatibleCoreTest {

    @Test
    void fallsBackWhenDisabled() {
        DispatchV2CompatibleCore core = TestDispatchV2Factory.compatibleCore(RouteChainDispatchV2Properties.defaults());
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-1",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertTrue(result.fallbackUsed());
    }

    @Test
    void delegatesWhenEnabled() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        DispatchV2CompatibleCore core = TestDispatchV2Factory.compatibleCore(properties);
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-2",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertFalse(result.fallbackUsed());
        assertTrue(result.decisionStages().containsAll(List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank")));
    }

    @Test
    void dispatchesWhenMlEnabledButNoSidecarExists() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setMlEnabled(true);
        DispatchV2CompatibleCore core = TestDispatchV2Factory.compatibleCore(properties);
        DispatchV2Result result = core.dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());
        assertFalse(result.fallbackUsed());
        assertTrue(result.degradeReasons().contains("eta-ml-unavailable-or-disabled-path"));
        assertTrue(result.degradeReasons().contains("pair-ml-unavailable-or-disabled-path"));
    }
}
