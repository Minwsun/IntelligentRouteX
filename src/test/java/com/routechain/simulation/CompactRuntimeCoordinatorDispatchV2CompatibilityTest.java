package com.routechain.simulation;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactRuntimeCoordinatorDispatchV2CompatibilityTest {

    @Test
    void coordinatorShouldKeepEvidenceLifecycleWhenDispatchV2IsEnabled() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setDeterministicLegacyFallbackEnabled(false);
        CompactRuntimeCoordinator coordinator = new CompactRuntimeCoordinator(CompactPolicyConfig.defaults(), properties);

        CompactDispatchDecision decision = coordinator.dispatch(
                List.of(order("ord-coord-1")),
                List.of(driver("drv-coord-1")),
                List.of(),
                12,
                0.22,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-16T04:20:00Z"));

        coordinator.beginDecision("run-v2", "COMPACT", Instant.parse("2026-04-16T04:20:00Z"), decision);
        coordinator.recordSelectedPlan(
                "run-v2",
                decision.plans().getFirst(),
                decision.selectedPlanEvidence().getFirst(),
                decision.weightSnapshotBefore(),
                Instant.parse("2026-04-16T04:20:30Z"));

        assertNotNull(coordinator.currentWeightSnapshot());
        assertNotNull(coordinator.weightEngine());
        assertNotNull(coordinator.latestEvidence());
        assertTrue(coordinator.latestEvidence().latestResolution() != null);
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
                Instant.parse("2026-04-16T04:20:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
