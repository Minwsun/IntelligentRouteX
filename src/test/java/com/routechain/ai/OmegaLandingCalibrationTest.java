package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OmegaLandingCalibrationTest {

    @Test
    void strongEndZoneContextProducesGoodLandingScore() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = driver("D-LANDING-CTX");
        DriverDecisionContext context = strongLandingContext(driver);
        GeoPoint endPoint = new GeoPoint(10.7830, 106.7081);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "computeLandingScoreFromContext",
                DriverDecisionContext.class,
                GeoPoint.class);
        method.setAccessible(true);

        double score = (double) method.invoke(agent, context, endPoint);

        assertTrue(score >= 0.60,
                "A strong post-drop zone should clear the good landing threshold");
    }

    @Test
    void softLandingAdjustmentCanLiftUnderscoredPlanIntoGoodZone() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = driver("D-LANDING-ADJUST");
        DriverDecisionContext context = strongLandingContext(driver);
        Order order = order("LANDING-1");
        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("LANDING-BUNDLE", List.of(order), 42000.0, 1),
                List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 2.0),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 11.0)));
        plan.setDeliveryCorridorScore(0.46);
        plan.setLastDropLandingScore(0.32);
        plan.setExpectedPostCompletionEmptyKm(1.6);
        plan.setExpectedNextOrderIdleMinutes(4.1);
        plan.setRemainingDropProximityScore(0.82);
        plan.setDeliveryZigZagPenalty(0.10);
        plan.setPostDropDemandProbability(0.22);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "applySoftLandingAdjustments",
                DispatchPlan.class,
                DriverDecisionContext.class,
                GeoPoint.class,
                double.class,
                double.class,
                double.class,
                double.class);
        method.setAccessible(true);
        method.invoke(
                agent,
                plan,
                context,
                order.getDropoffPoint(),
                0.72,
                0.68,
                0.08,
                0.10);

        assertTrue(plan.getLastDropLandingScore() >= 0.60,
                "Soft landing adjustment should recover a genuinely strong end-zone");
        assertTrue(plan.getPostDropDemandProbability() >= 0.70,
                "Landing adjustment should preserve strong post-drop opportunity in the plan");
    }

    @Test
    void harshWeatherLandingAdjustmentDoesNotOverinflatePostDropOpportunity() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = driver("D-LANDING-HARSH");
        DriverDecisionContext context = harshLandingStressContext(driver);
        Order order = order("LANDING-HARSH-1");
        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("LANDING-HARSH-BUNDLE", List.of(order), 42000.0, 1),
                List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 2.0),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 11.0)));
        plan.setDeliveryCorridorScore(0.62);
        plan.setLastDropLandingScore(0.78);
        plan.setExpectedPostCompletionEmptyKm(1.3);
        plan.setExpectedNextOrderIdleMinutes(2.7);
        plan.setRemainingDropProximityScore(0.80);
        plan.setDeliveryZigZagPenalty(0.10);
        plan.setPostDropDemandProbability(0.74);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "applySoftLandingAdjustments",
                DispatchPlan.class,
                DriverDecisionContext.class,
                GeoPoint.class,
                double.class,
                double.class,
                double.class,
                double.class);
        method.setAccessible(true);
        method.invoke(
                agent,
                plan,
                context,
                order.getDropoffPoint(),
                0.66,
                0.60,
                0.72,
                0.64);

        assertTrue(plan.getLastDropLandingScore() <= 0.55,
                "Harsh-weather landing should stay capped instead of inflating into a near-perfect hot zone");
        assertTrue(plan.getPostDropDemandProbability() <= 0.40,
                "Harsh-weather post-drop opportunity should be cut back when weather and idle risk are both high");
        assertTrue(plan.getExpectedNextOrderIdleMinutes() >= 3.0,
                "Harsh-weather calibration should keep realistic next-idle expectations");
    }

    private static DriverDecisionContext strongLandingContext(Driver driver) {
        return new DriverDecisionContext(
                driver,
                List.of(order("CTX-1")),
                List.of(),
                0.26,
                1.10,
                1.20,
                1.34,
                1.38,
                1.42,
                0.24,
                0.28,
                0.12,
                0.24,
                0.18,
                0.72,
                0.26,
                0.34,
                0.18,
                0.12,
                0.16,
                0.76,
                0.12,
                0.68,
                2.2,
                2,
                9.5,
                1,
                1,
                2,
                false,
                0.72,
                4.6,
                0.22,
                0.82,
                0.18,
                List.of(new DriverDecisionContext.DropCorridorCandidate(
                        "C-STRONG",
                        new GeoPoint(10.7831, 106.7080),
                        0.86,
                        1.8,
                        0.18,
                        0.10)),
                List.of(
                        new DriverDecisionContext.EndZoneCandidate(
                                new GeoPoint(10.7830, 106.7081),
                                0.78,
                                0.2,
                                0.10,
                                0.16,
                                1.9,
                                1.1,
                                0.3,
                                0.82,
                                0.12),
                        new DriverDecisionContext.EndZoneCandidate(
                                new GeoPoint(10.7862, 106.7118),
                                0.42,
                                0.9,
                                0.30,
                                0.42,
                                0.8,
                                0.4,
                                0.3,
                                0.28,
                                0.64)),
                StressRegime.NORMAL);
    }

    private static DriverDecisionContext harshLandingStressContext(Driver driver) {
        return new DriverDecisionContext(
                driver,
                List.of(order("CTX-HARSH-1")),
                List.of(),
                0.28,
                1.05,
                0.82,
                0.88,
                0.84,
                0.80,
                0.30,
                0.34,
                0.78,
                0.76,
                0.70,
                0.34,
                0.22,
                0.28,
                0.82,
                0.76,
                0.74,
                0.24,
                0.36,
                0.18,
                4.6,
                1,
                2.6,
                0,
                0,
                1,
                true,
                0.34,
                1.0,
                0.18,
                0.40,
                0.74,
                List.of(new DriverDecisionContext.DropCorridorCandidate(
                        "C-HARSH",
                        new GeoPoint(10.7831, 106.7080),
                        0.46,
                        1.1,
                        0.72,
                        0.78)),
                List.of(
                        new DriverDecisionContext.EndZoneCandidate(
                                new GeoPoint(10.7830, 106.7081),
                                0.54,
                                0.34,
                                0.76,
                                0.78,
                                0.9,
                                0.8,
                                0.7,
                                0.34,
                                0.78),
                        new DriverDecisionContext.EndZoneCandidate(
                                new GeoPoint(10.7862, 106.7118),
                                0.42,
                                0.22,
                                0.82,
                                0.84,
                                0.6,
                                0.5,
                                0.8,
                                0.24,
                                0.82)),
                StressRegime.STRESS);
    }

    private static Driver driver(String id) {
        return new Driver(
                id,
                "Landing Driver",
                new GeoPoint(10.7766, 106.7008),
                "R1",
                VehicleType.MOTORBIKE);
    }

    private static Order order(String id) {
        return new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(10.7769, 106.7010),
                new GeoPoint(10.7830, 106.7081),
                "R2",
                42000.0,
                60,
                Instant.parse("2026-04-08T08:00:00Z"));
    }
}
