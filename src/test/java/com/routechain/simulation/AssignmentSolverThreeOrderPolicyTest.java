package com.routechain.simulation;

import com.routechain.ai.StressRegime;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class AssignmentSolverThreeOrderPolicyTest {

    @Test
    void hardThreePolicyPrefersVisibleWaveOverHigherScoredSingle() {
        Driver driver = new Driver(
                "DRV-1",
                "Driver 1",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan single = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("SINGLE", List.of(order("O-1", 0)), 42000.0, 1),
                List.of(
                        new DispatchPlan.Stop("O-1", new GeoPoint(10.7767, 106.7010), DispatchPlan.Stop.StopType.PICKUP, 3.0),
                        new DispatchPlan.Stop("O-1", new GeoPoint(10.7815, 106.7072), DispatchPlan.Stop.StopType.DROPOFF, 13.0)));
        single.setTotalScore(0.92);
        single.setConfidence(0.72);
        single.setHardThreeOrderPolicyActive(true);
        single.setStressFallbackOnly(true);
        single.setStressRegime(StressRegime.NORMAL);

        List<Order> waveOrders = List.of(order("O-2", 10), order("O-3", 20), order("O-4", 30));
        DispatchPlan wave = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("WAVE", waveOrders, 128000.0, 3),
                List.of(
                        new DispatchPlan.Stop("O-2", new GeoPoint(10.7768, 106.7011), DispatchPlan.Stop.StopType.PICKUP, 2.5),
                        new DispatchPlan.Stop("O-3", new GeoPoint(10.7769, 106.7012), DispatchPlan.Stop.StopType.PICKUP, 4.0),
                        new DispatchPlan.Stop("O-4", new GeoPoint(10.7770, 106.7013), DispatchPlan.Stop.StopType.PICKUP, 5.5),
                        new DispatchPlan.Stop("O-2", new GeoPoint(10.7825, 106.7078), DispatchPlan.Stop.StopType.DROPOFF, 12.0),
                        new DispatchPlan.Stop("O-3", new GeoPoint(10.7830, 106.7083), DispatchPlan.Stop.StopType.DROPOFF, 15.0),
                        new DispatchPlan.Stop("O-4", new GeoPoint(10.7834, 106.7088), DispatchPlan.Stop.StopType.DROPOFF, 18.0)));
        wave.setTotalScore(0.81);
        wave.setConfidence(0.76);
        wave.setHardThreeOrderPolicyActive(true);
        wave.setWaveLaunchEligible(true);
        wave.setStressRegime(StressRegime.NORMAL);

        AssignmentSolver solver = new AssignmentSolver();
        List<DispatchPlan> selected = solver.solve(List.of(single, wave));

        assertEquals(1, selected.size());
        assertEquals("WAVE", selected.get(0).getBundle().bundleId());
    }

    @Test
    void realOrderPlanWinsBeforeHoldPlanForSameDriver() {
        Driver driver = new Driver(
                "DRV-2",
                "Driver 2",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan hold = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("HOLD", List.of(), 0.0, 0),
                List.of());
        hold.setTotalScore(0.95);
        hold.setConfidence(0.40);
        hold.setWaitingForThirdOrder(true);
        hold.setHardThreeOrderPolicyActive(true);
        hold.setStressRegime(StressRegime.NORMAL);

        List<Order> compactOrders = List.of(order("O-5", 0), order("O-6", 10));
        DispatchPlan compactTwo = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("COMPACT-2", compactOrders, 82000.0, 2),
                List.of(
                        new DispatchPlan.Stop("O-5", new GeoPoint(10.7768, 106.7011), DispatchPlan.Stop.StopType.PICKUP, 2.0),
                        new DispatchPlan.Stop("O-6", new GeoPoint(10.7769, 106.7012), DispatchPlan.Stop.StopType.PICKUP, 3.0),
                        new DispatchPlan.Stop("O-5", new GeoPoint(10.7825, 106.7078), DispatchPlan.Stop.StopType.DROPOFF, 11.0),
                        new DispatchPlan.Stop("O-6", new GeoPoint(10.7830, 106.7083), DispatchPlan.Stop.StopType.DROPOFF, 14.0)));
        compactTwo.setTotalScore(0.72);
        compactTwo.setConfidence(0.74);
        compactTwo.setStressRegime(StressRegime.NORMAL);

        AssignmentSolver solver = new AssignmentSolver();
        List<DispatchPlan> selected = solver.solve(List.of(hold, compactTwo));

        assertEquals(1, selected.size());
        assertEquals("COMPACT-2", selected.get(0).getBundle().bundleId());
    }

    @Test
    void quotaSkippedBorrowedPlanMustNotBlockEmergencyFallback() {
        Driver zoneOneLeader = new Driver(
                "DRV-A",
                "Driver A",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);
        Driver zoneOneRunnerUp = new Driver(
                "DRV-B",
                "Driver B",
                new GeoPoint(10.7770, 106.7013),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan borrowedPrimary = plan(
                zoneOneLeader,
                "BORROW-A",
                List.of(order("O-7", 0)),
                SelectionBucket.BORROWED_COVERAGE,
                0.91,
                0.82,
                0.42);
        DispatchPlan borrowedQuotaSkipped = plan(
                zoneOneRunnerUp,
                "BORROW-B",
                List.of(order("O-8", 10)),
                SelectionBucket.BORROWED_COVERAGE,
                0.88,
                0.79,
                0.40);
        DispatchPlan emergencyRescue = plan(
                zoneOneRunnerUp,
                "EMERGENCY-C",
                List.of(order("O-9", 20, "R2")),
                SelectionBucket.EMERGENCY_COVERAGE,
                0.74,
                0.77,
                0.18);

        AssignmentSolver solver = new AssignmentSolver();
        List<DispatchPlan> selected = solver.solve(List.of(
                borrowedPrimary,
                borrowedQuotaSkipped,
                emergencyRescue));

        assertEquals(2, selected.size());
        assertIterableEquals(
                List.of("BORROW-A", "EMERGENCY-C"),
                selected.stream().map(plan -> plan.getBundle().bundleId()).toList());
    }

    private static DispatchPlan plan(Driver driver,
                                     String bundleId,
                                     List<Order> orders,
                                     SelectionBucket bucket,
                                     double totalScore,
                                     double confidence,
                                     double borrowedDependencyScore) {
        GeoPoint pickup = orders.get(0).getPickupPoint();
        GeoPoint dropoff = orders.get(0).getDropoffPoint();
        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle(bundleId, orders, 42000.0 * orders.size(), orders.size()),
                List.of(
                        new DispatchPlan.Stop(orders.get(0).getId(), pickup, DispatchPlan.Stop.StopType.PICKUP, 2.5),
                        new DispatchPlan.Stop(orders.get(0).getId(), dropoff, DispatchPlan.Stop.StopType.DROPOFF, 12.0)));
        plan.setSelectionBucket(bucket);
        plan.setTotalScore(totalScore);
        plan.setConfidence(confidence);
        plan.setBorrowedDependencyScore(borrowedDependencyScore);
        return plan;
    }

    private static Order order(String id, int createdOffsetSeconds) {
        return order(id, createdOffsetSeconds, "R1");
    }

    private static Order order(String id, int createdOffsetSeconds, String pickupRegionId) {
        Instant createdAt = Instant.parse("2026-03-25T00:00:00Z").plusSeconds(createdOffsetSeconds);
        return new Order(
                id,
                "CUS-" + id,
                pickupRegionId,
                new GeoPoint(10.7768 + createdOffsetSeconds * 0.00001, 106.7011),
                new GeoPoint(10.7825 + createdOffsetSeconds * 0.00001, 106.7078),
                "R2",
                42000.0,
                70,
                createdAt);
    }
}
