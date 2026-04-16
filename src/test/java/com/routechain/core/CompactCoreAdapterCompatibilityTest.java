package com.routechain.core;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompactCoreAdapterCompatibilityTest {

    @Test
    void coreShouldExposeCompatibilityFacadeAndSupportV2Dispatch() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setDeterministicLegacyFallbackEnabled(false);
        CompactCoreAdapter adapter = new CompactCoreAdapter(CompactPolicyConfig.defaults(), properties);

        CompactDispatchDecision decision = adapter.dispatch(
                List.of(order("ord-adapter-1")),
                List.of(driver("drv-adapter-1")),
                List.of(),
                12,
                0.18,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-16T04:10:00Z"));

        assertNotNull(adapter.core());
        assertTrueCompat(adapter.core().isDispatchV2Enabled());
        assertFalse(decision.plans().isEmpty());
        assertEquals("dispatch-v2", decision.plans().getFirst().getPipelineVersion());
    }

    private void assertTrueCompat(boolean condition) {
        org.junit.jupiter.api.Assertions.assertTrue(condition);
    }

    private Driver driver(String id) {
        return new Driver(id, id, new GeoPoint(10.7765, 106.7009), "R1", VehicleType.MOTORBIKE);
    }

    private Order order(String id) {
        Order order = new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(10.7768, 106.7010),
                new GeoPoint(10.7820, 106.7070),
                "R2",
                42_000.0,
                55,
                Instant.parse("2026-04-16T04:10:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
