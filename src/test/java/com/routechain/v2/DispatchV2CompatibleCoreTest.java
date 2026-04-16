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
        DispatchV2CompatibleCore core = new DispatchV2CompatibleCore(RouteChainDispatchV2Properties.defaults());
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
        DispatchV2CompatibleCore core = new DispatchV2CompatibleCore(properties);
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-2",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertFalse(result.fallbackUsed());
    }
}

