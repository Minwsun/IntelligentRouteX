package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmegaStressRegimeTest {

    @Test
    void classifiesSevereStressFromStormAndHighExposure() {
        StressRegime regime = OmegaDispatchAgent.classifyStressRegime(
                0.78,
                WeatherProfile.STORM,
                0.86,
                0.81,
                0.73,
                0.88,
                0.69,
                7);

        assertEquals(StressRegime.SEVERE_STRESS, regime);
    }

    @Test
    void fallbackRejectsNegativeSlackUnderStress() {
        boolean safe = OmegaDispatchAgent.isFallbackSafe(
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN,
                -2.0,
                0.71,
                1.4,
                0.18,
                0.3,
                0.22);

        assertFalse(safe, "Heavy-rain fallback should reject negative-slack assignments");
    }

    @Test
    void fallbackAllowsCleanShortSingleInNormal() {
        boolean safe = OmegaDispatchAgent.isFallbackSafe(
                StressRegime.NORMAL,
                WeatherProfile.CLEAR,
                3.0,
                0.83,
                1.1,
                0.12,
                0.2,
                0.14);

        assertTrue(safe, "Normal fallback should allow a clean short single-order assignment");
    }

    @Test
    void hardThreeOrderLaunchOnlyAppliesInCleanRegime() {
        Driver driver = new Driver(
                "D-STRESS",
                "Policy Driver",
                new GeoPoint(10.77, 106.70),
                "R1",
                VehicleType.MOTORBIKE);

        DriverDecisionContext cleanContext = new DriverDecisionContext(
                driver,
                List.of(
                        order("CLEAN-1", 0),
                        order("CLEAN-2", 10),
                        order("CLEAN-3", 20)),
                List.of(),
                0.42,
                1.1,
                1.4,
                1.5,
                1.5,
                1.3,
                0.48,
                0.52,
                0.12,
                0.28,
                0.18,
                0.74,
                0.24,
                0.3,
                0.2,
                0.10,
                0.18,
                0.76,
                0.22,
                0.6,
                2.0,
                3,
                9.0,
                2,
                1,
                3,
                false,
                0.82,
                5.0,
                0.78,
                0.5,
                0.2,
                List.of(),
                List.of(),
                StressRegime.STRESS);

        DriverDecisionContext harshContext = new DriverDecisionContext(
                driver,
                List.of(),
                List.of(),
                0.52,
                1.1,
                1.0,
                1.0,
                0.9,
                0.8,
                0.56,
                0.60,
                0.82,
                0.68,
                0.52,
                0.18,
                0.42,
                0.3,
                0.2,
                0.78,
                0.82,
                0.12,
                0.92,
                0.3,
                3.5,
                1,
                2.0,
                0,
                0,
                2,
                true,
                0.18,
                0.6,
                0.22,
                0.1,
                0.8,
                List.of(),
                List.of(),
                StressRegime.STRESS);

        assertTrue(DriverPlanGenerator.requiresHardThreeOrderLaunch(
                OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                cleanContext));
        assertFalse(DriverPlanGenerator.requiresHardThreeOrderLaunch(
                OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC,
                harshContext));
    }

    @Test
    void realAssignedCoverageIgnoresHoldOnlyPlans() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-HOLD-COVERAGE",
                "Hold Coverage Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan holdOnly = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("HOLD-ONLY", List.of(), 0.0, 0),
                List.of());
        holdOnly.setWaitingForThirdOrder(true);
        holdOnly.setHardThreeOrderPolicyActive(true);

        DispatchPlan realSingle = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("REAL-SINGLE", List.of(order("REAL-1", 0)), 42000.0, 1),
                List.of());

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "countRealAssignedPlans",
                List.class);
        method.setAccessible(true);
        int coverageUnits = (int) method.invoke(agent, List.of(holdOnly, realSingle));

        assertEquals(1, coverageUnits,
                "Hold-only shortlist entries should not count as real coverage units");
    }

    @Test
    void minimumCoverageTargetScalesBeyondLegacyOneToThreeWindow() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "minimumCoverageTarget",
                double.class,
                double.class,
                WeatherProfile.class,
                int.class,
                int.class);
        method.setAccessible(true);

        int target = (int) method.invoke(
                agent,
                0.22,
                0.18,
                WeatherProfile.CLEAR,
                18,
                12);

        assertTrue(target >= 5,
                "Coverage target should scale with pending demand when clean-regime execution is too sparse");
    }

    private static Order order(String id, int createdOffsetSeconds) {
        Instant createdAt = Instant.parse("2026-03-25T00:00:00Z").plusSeconds(createdOffsetSeconds);
        return new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(10.7768 + createdOffsetSeconds * 0.00001, 106.7011),
                new GeoPoint(10.7825 + createdOffsetSeconds * 0.00001, 106.7078),
                "R2",
                42000.0,
                70,
                createdAt);
    }
}
