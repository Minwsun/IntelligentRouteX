package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;
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
    void bundledWaveCountsAsSingleCoveragePlan() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-WAVE-COVERAGE",
                "Wave Coverage Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan wave = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle(
                        "WAVE-COVERAGE",
                        List.of(order("W-1", 0), order("W-2", 10), order("W-3", 20)),
                        118000.0,
                        3),
                List.of());

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "countRealAssignedPlans",
                List.class);
        method.setAccessible(true);
        int coverageUnits = (int) method.invoke(agent, List.of(wave));

        assertEquals(1, coverageUnits,
                "Coverage gating should count one executable driver-plan, not the wave bundle size");
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

    @Test
    void heavyRainStressGateRejectsWeakRescueFallbackButKeepsStrongLocalRescue() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-STRESS-GATE",
                "Stress Gate Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan weakBorrowedFallback = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("WEAK-BORROWED", List.of(order("WEAK-1", 0)), 42000.0, 1),
                List.of());
        weakBorrowedFallback.setSelectionBucket(SelectionBucket.BORROWED_COVERAGE);
        weakBorrowedFallback.setStressFallbackOnly(true);
        weakBorrowedFallback.setStressRescueScore(0.35);

        DispatchPlan strongLocalFallback = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("STRONG-LOCAL", List.of(order("STRONG-1", 10)), 42000.0, 1),
                List.of());
        strongLocalFallback.setSelectionBucket(SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD);
        strongLocalFallback.setStressFallbackOnly(true);
        strongLocalFallback.setStressRescueScore(0.52);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "passesStressRescueGate",
                DispatchPlan.class,
                StressRegime.class,
                WeatherProfile.class);
        method.setAccessible(true);

        boolean weakAccepted = (boolean) method.invoke(
                agent,
                weakBorrowedFallback,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN);
        boolean strongAccepted = (boolean) method.invoke(
                agent,
                strongLocalFallback,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN);

        assertFalse(weakAccepted,
                "Heavy-rain stress gate should reject weak borrowed rescue plans");
        assertTrue(strongAccepted,
                "Heavy-rain stress gate should keep strong short local rescues");
    }

    @Test
    void heavyRainLocalMainlineSingleIsNotDowngradedIntoFallbackOnly() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-LOCAL-MAINLINE",
                "Local Mainline Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan localSingle = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("LOCAL-SINGLE", List.of(order("LOCAL-1", 0)), 42000.0, 1),
                List.of());
        localSingle.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "syncThreeOrderPolicyFlags",
                DispatchPlan.class,
                DriverDecisionContext.class,
                StressRegime.class);
        method.setAccessible(true);
        method.invoke(
                agent,
                localSingle,
                harshStressContext(driver),
                StressRegime.STRESS);

        assertFalse(localSingle.isStressFallbackOnly(),
                "Harsh weather should not relabel a same-zone mainline single as fallback-only rescue");
    }

    @Test
    void heavyRainFallbackSaturationGuardCapsRescueOverflow() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-FALLBACK-CAP",
                "Fallback Cap Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan localSingle = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("LOCAL-SINGLE", List.of(order("CAP-1", 0)), 42000.0, 1),
                List.of());
        localSingle.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "applyFallbackSaturationGuard",
                List.class,
                int.class,
                double.class,
                WeatherProfile.class);
        method.setAccessible(true);

        int guarded = (int) method.invoke(
                agent,
                List.of(localSingle),
                5,
                0.85,
                WeatherProfile.HEAVY_RAIN);

        assertTrue(guarded < 5,
                "Heavy-rain saturation guard should cap fallback expansion instead of allowing unbounded rescue");
    }

    @Test
    void heavyRainContinuationScorePenalizesBorrowedEmptyLanding() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-CONTINUATION-STRESS",
                "Continuation Stress Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan cleanLanding = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("CLEAN-LANDING", List.of(order("CL-1", 0)), 42000.0, 1),
                List.of());
        cleanLanding.setServiceTier("instant");
        cleanLanding.setDeliveryCorridorScore(0.72);
        cleanLanding.setLastDropLandingScore(0.70);
        cleanLanding.setNextOrderAcquisitionScore(0.58);
        cleanLanding.setContinuationValueScore(0.52);
        cleanLanding.setPostDropDemandProbability(0.66);
        cleanLanding.setExpectedPostCompletionEmptyKm(0.8);
        cleanLanding.setExpectedNextOrderIdleMinutes(2.0);

        DispatchPlan harshBorrowedLanding = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("HARSH-BORROWED", List.of(order("HB-1", 10)), 42000.0, 1),
                List.of());
        harshBorrowedLanding.setServiceTier("instant");
        harshBorrowedLanding.setDeliveryCorridorScore(0.72);
        harshBorrowedLanding.setLastDropLandingScore(0.70);
        harshBorrowedLanding.setNextOrderAcquisitionScore(0.58);
        harshBorrowedLanding.setContinuationValueScore(0.52);
        harshBorrowedLanding.setPostDropDemandProbability(0.66);
        harshBorrowedLanding.setExpectedPostCompletionEmptyKm(2.2);
        harshBorrowedLanding.setExpectedNextOrderIdleMinutes(5.5);
        harshBorrowedLanding.setBorrowedDependencyScore(0.34);
        harshBorrowedLanding.setEmptyRiskAfter(0.68);
        harshBorrowedLanding.setPredictedDeadheadKm(2.1);
        harshBorrowedLanding.setHarshWeatherStress(true);
        harshBorrowedLanding.setStressFallbackOnly(true);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "computeContinuationScore",
                DispatchPlan.class,
                StressRegime.class);
        method.setAccessible(true);

        double cleanScore = (double) method.invoke(agent, cleanLanding, StressRegime.NORMAL);
        double harshScore = (double) method.invoke(agent, harshBorrowedLanding, StressRegime.STRESS);

        assertTrue(cleanScore > harshScore,
                "Harsh-weather borrowed landings should lose continuation credit versus clean local landings");
    }

    @Test
    void heavyRainPositioningCalibrationCapsOptimisticBorrowedLanding() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-POSITIONING-STRESS",
                "Positioning Stress Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan harshBorrowedLanding = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("POSITIONING-HARSH", List.of(order("PH-1", 0)), 42000.0, 1),
                List.of());
        harshBorrowedLanding.setPostDropDemandProbability(0.28);
        harshBorrowedLanding.setLastDropLandingScore(0.24);
        harshBorrowedLanding.setGraphAffinityScore(0.82);
        harshBorrowedLanding.setExpectedPostCompletionEmptyKm(2.3);
        harshBorrowedLanding.setCongestionPenalty(0.52);
        harshBorrowedLanding.setBorrowedDependencyScore(0.30);
        harshBorrowedLanding.setPredictedDeadheadKm(2.1);
        harshBorrowedLanding.setOnTimeProbability(0.68);
        harshBorrowedLanding.setHarshWeatherStress(true);
        harshBorrowedLanding.setStressRegime(StressRegime.STRESS);

        Method neutralMethod = OmegaDispatchAgent.class.getDeclaredMethod(
                "neutralPositioningValue",
                DispatchPlan.class);
        neutralMethod.setAccessible(true);
        double neutral = (double) neutralMethod.invoke(agent, harshBorrowedLanding);

        Method calibratedMethod = OmegaDispatchAgent.class.getDeclaredMethod(
                "calibratedPositioningValueScore",
                DispatchPlan.class,
                double.class);
        calibratedMethod.setAccessible(true);
        double calibrated = (double) calibratedMethod.invoke(agent, harshBorrowedLanding, 0.95);

        assertTrue(calibrated <= neutral + 0.021,
                "Harsh-weather borrowed landing should not keep an overly optimistic positioning uplift");
    }

    private static DriverDecisionContext harshStressContext(Driver driver) {
        return new DriverDecisionContext(
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
