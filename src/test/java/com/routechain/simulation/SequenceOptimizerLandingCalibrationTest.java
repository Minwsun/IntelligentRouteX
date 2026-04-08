package com.routechain.simulation;

import com.routechain.ai.DriverDecisionContext;
import com.routechain.ai.StressRegime;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SequenceOptimizerLandingCalibrationTest {

    @Test
    void routeObjectiveRecognizesStrongLastDropZone() {
        Driver driver = new Driver(
                "D-SEQUENCE-LANDING",
                "Sequence Landing Driver",
                new GeoPoint(10.7766, 106.7008),
                "R1",
                VehicleType.MOTORBIKE);
        Order order = new Order(
                "SEQ-1",
                "CUS-SEQ-1",
                "R1",
                new GeoPoint(10.7769, 106.7010),
                new GeoPoint(10.7830, 106.7081),
                "R2",
                42000.0,
                60,
                Instant.parse("2026-04-08T08:00:00Z"));

        DriverDecisionContext context = new DriverDecisionContext(
                driver,
                List.of(order),
                List.of(),
                0.24,
                1.05,
                1.12,
                1.28,
                1.34,
                1.40,
                0.22,
                0.27,
                0.10,
                0.22,
                0.16,
                0.74,
                0.24,
                0.32,
                0.18,
                0.10,
                0.14,
                0.78,
                0.12,
                0.66,
                2.0,
                1,
                10.0,
                1,
                1,
                1,
                false,
                0.70,
                4.2,
                0.20,
                0.80,
                0.16,
                List.of(),
                List.of(new DriverDecisionContext.EndZoneCandidate(
                        new GeoPoint(10.7830, 106.7081),
                        0.80,
                        0.1,
                        0.08,
                        0.14,
                        1.8,
                        1.0,
                        0.2,
                        0.84,
                        0.10)),
                StressRegime.NORMAL);

        SequenceOptimizer optimizer = new SequenceOptimizer(
                0.24,
                com.routechain.domain.Enums.WeatherProfile.CLEAR,
                false,
                StressRegime.NORMAL,
                context);

        SequenceOptimizer.RouteObjectiveMetrics metrics = optimizer.evaluateRouteObjective(
                driver,
                List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 2.0),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 11.0)),
                List.of(order));

        assertTrue(metrics.lastDropLandingScore() >= 0.60,
                "Sequence objective should recognize a strong landing zone as good");
        assertTrue(metrics.expectedPostCompletionEmptyKm() <= 0.8,
                "Strong landing zone should imply a short empty leg after completion");
    }
}
