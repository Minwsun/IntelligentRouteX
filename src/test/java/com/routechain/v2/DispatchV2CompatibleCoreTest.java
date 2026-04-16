package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactPolicyConfig;
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

class DispatchV2CompatibleCoreTest {

    @Test
    void shouldDispatchThroughV2AndExposeCompatibilitySurface() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setDeterministicLegacyFallbackEnabled(false);
        DispatchV2CompatibleCore core = new DispatchV2CompatibleCore(CompactPolicyConfig.defaults(), properties);

        CompactDispatchDecision decision = core.dispatch(
                List.of(order("ord-v2-1", 10.7768, 106.7010, 10.7820, 106.7070)),
                List.of(driver("drv-v2-1", 10.7765, 106.7009)),
                List.of(),
                12,
                0.20,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-16T04:00:00Z"));

        assertFalse(decision.plans().isEmpty());
        assertEquals("dispatch-v2", decision.plans().getFirst().getPipelineVersion());
        assertNotNull(decision.plans().getFirst().getRouteProposalId());
        assertNotNull(decision.plans().getFirst().getBufferWindowId());
        assertNotNull(core.adaptiveWeightEngine());
        assertNotNull(core.currentWeightSnapshot());
    }

    private Driver driver(String id, double lat, double lng) {
        return new Driver(id, id, new GeoPoint(lat, lng), "R1", VehicleType.MOTORBIKE);
    }

    private Order order(String id, double pickupLat, double pickupLng, double dropLat, double dropLng) {
        Order order = new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(dropLat, dropLng),
                "R2",
                42_000.0,
                55,
                Instant.parse("2026-04-16T04:00:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
