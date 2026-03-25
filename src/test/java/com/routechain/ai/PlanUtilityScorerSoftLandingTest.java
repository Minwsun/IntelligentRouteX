package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanUtilityScorerSoftLandingTest {

    private final PlanUtilityScorer scorer = new PlanUtilityScorer();

    @Test
    void betterLandingPlanShouldOutscoreDeadEndPlan() {
        DispatchPlan goodLanding = createBasePlan("GOOD");
        goodLanding.setRemainingDropProximityScore(0.86);
        goodLanding.setDeliveryCorridorScore(0.84);
        goodLanding.setLastDropLandingScore(0.88);
        goodLanding.setExpectedPostCompletionEmptyKm(0.45);
        goodLanding.setExpectedNextOrderIdleMinutes(1.6);
        goodLanding.setDeliveryZigZagPenalty(0.08);
        goodLanding.setEndZoneOpportunity(0.76);
        goodLanding.setNextOrderAcquisitionScore(0.79);

        DispatchPlan deadEnd = createBasePlan("BAD");
        deadEnd.setRemainingDropProximityScore(0.44);
        deadEnd.setDeliveryCorridorScore(0.38);
        deadEnd.setLastDropLandingScore(0.26);
        deadEnd.setExpectedPostCompletionEmptyKm(2.75);
        deadEnd.setExpectedNextOrderIdleMinutes(6.1);
        deadEnd.setDeliveryZigZagPenalty(0.42);
        deadEnd.setEndZoneOpportunity(0.34);
        deadEnd.setNextOrderAcquisitionScore(0.31);

        double goodScore = scorer.score(goodLanding, StressRegime.NORMAL);
        double badScore = scorer.score(deadEnd, StressRegime.NORMAL);

        assertTrue(goodScore > badScore,
                "Plan ending in a better landing zone should outscore a dead-end route");
    }

    @Test
    void zigZagRouteShouldLoseToCorridorAlignedRoute() {
        DispatchPlan corridorAligned = createBasePlan("ALIGNED");
        corridorAligned.setRemainingDropProximityScore(0.80);
        corridorAligned.setDeliveryCorridorScore(0.90);
        corridorAligned.setLastDropLandingScore(0.74);
        corridorAligned.setExpectedPostCompletionEmptyKm(0.70);
        corridorAligned.setDeliveryZigZagPenalty(0.06);

        DispatchPlan zigZag = createBasePlan("ZIGZAG");
        zigZag.setRemainingDropProximityScore(0.48);
        zigZag.setDeliveryCorridorScore(0.33);
        zigZag.setLastDropLandingScore(0.69);
        zigZag.setExpectedPostCompletionEmptyKm(1.25);
        zigZag.setDeliveryZigZagPenalty(0.62);

        double alignedScore = scorer.score(corridorAligned, StressRegime.NORMAL);
        double zigZagScore = scorer.score(zigZag, StressRegime.NORMAL);

        assertTrue(alignedScore > zigZagScore,
                "Corridor-aligned delivery should beat a zig-zag route with similar base SLA");
    }

    private DispatchPlan createBasePlan(String suffix) {
        GeoPoint driverLocation = new GeoPoint(10.7766, 106.7008);
        Driver driver = new Driver("D-" + suffix, "Driver " + suffix, driverLocation, "R1", VehicleType.MOTORBIKE);
        Instant createdAt = Instant.parse("2026-03-24T08:00:00Z");

        Order o1 = new Order("O1-" + suffix, "C1-" + suffix, "R1",
                new GeoPoint(10.7769, 106.7010),
                new GeoPoint(10.7790, 106.7045),
                "R2", 32000, 45, createdAt);
        Order o2 = new Order("O2-" + suffix, "C2-" + suffix, "R1",
                new GeoPoint(10.7771, 106.7013),
                new GeoPoint(10.7803, 106.7056),
                "R2", 33000, 48, createdAt.plusSeconds(20));
        Order o3 = new Order("O3-" + suffix, "C3-" + suffix, "R1",
                new GeoPoint(10.7773, 106.7015),
                new GeoPoint(10.7812, 106.7071),
                "R3", 34000, 50, createdAt.plusSeconds(40));

        List<Order> orders = List.of(o1, o2, o3);
        List<DispatchPlan.Stop> sequence = List.of(
                new DispatchPlan.Stop(o1.getId(), o1.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 2.0),
                new DispatchPlan.Stop(o2.getId(), o2.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 3.2),
                new DispatchPlan.Stop(o3.getId(), o3.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 4.4),
                new DispatchPlan.Stop(o1.getId(), o1.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 11.0),
                new DispatchPlan.Stop(o2.getId(), o2.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 15.0),
                new DispatchPlan.Stop(o3.getId(), o3.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 19.0)
        );

        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("B-" + suffix, orders, 0.0, orders.size()),
                sequence);
        plan.setPredictedTotalMinutes(19.0);
        plan.setPredictedDeadheadKm(0.85);
        plan.setOnTimeProbability(0.84);
        plan.setLateRisk(0.16);
        plan.setCancellationRisk(0.07);
        plan.setDriverProfit(76000);
        plan.setCustomerFee(33000);
        plan.setBundleEfficiency(0.74);
        plan.setEndZoneOpportunity(0.60);
        plan.setNextOrderAcquisitionScore(0.58);
        plan.setCongestionPenalty(0.18);
        plan.setRepositionPenalty(0.10);
        return plan;
    }
}
