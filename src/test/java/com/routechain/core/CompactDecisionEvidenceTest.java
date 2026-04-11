package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactDecisionEvidenceTest {

    @Test
    void selectedPlanEvidenceShouldMatchWinnerAndExplanationTruth() {
        CompactDispatchCore core = new CompactDispatchCore();
        Order left = order("ORD-E1", 10.7767, 106.7010, 10.7820, 106.7070);
        Order right = order("ORD-E2", 10.7769, 106.7012, 10.7822, 106.7072);

        CompactDispatchDecision decision = core.dispatch(
                List.of(left, right),
                List.of(driver("DRV-E1", 10.7765, 106.7009)),
                List.of(),
                12,
                0.18,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-12T04:20:00Z"));

        CompactSelectedPlanEvidence evidence = decision.selectedPlanEvidence().get(0);
        CompactDecisionExplanation explanation = decision.explanations().get(0);

        assertEquals(decision.plans().get(0).getTraceId(), evidence.traceId());
        assertEquals(decision.plans().get(0).getBundle().bundleId(), evidence.bundleId());
        assertEquals(explanation.planType(), evidence.planType());
        assertEquals(explanation.summary(), evidence.explanationSummary());
        assertFalse(evidence.orderIds().isEmpty());
        assertNotNull(evidence.featureVector());
        assertNotNull(evidence.scoreBreakdown());
        assertTrue(explanation.summary().contains(evidence.bundleId()));
        assertEquals(
                decision.plans().get(0).getOrders().stream().map(Order::getId).collect(Collectors.toSet()),
                Set.copyOf(evidence.orderIds()));
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
                42000.0,
                55,
                Instant.parse("2026-04-12T04:20:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
