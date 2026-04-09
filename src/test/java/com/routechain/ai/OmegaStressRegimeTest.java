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
    void cleanFallbackCoverageDoesNotCountAsStressDowngrade() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "shouldMarkFallbackAsStressDowngrade",
                StressRegime.class,
                WeatherProfile.class,
                boolean.class,
                boolean.class,
                double.class,
                double.class,
                double.class);
        method.setAccessible(true);

        boolean downgraded = (boolean) method.invoke(
                agent,
                StressRegime.NORMAL,
                WeatherProfile.CLEAR,
                true,
                false,
                1.0,
                0.84,
                0.16);

        assertFalse(downgraded,
                "Clean same-zone fallback coverage should stay out of stress-downgrade accounting");
    }

    @Test
    void severeOrHarshFallbackStillCountsAsStressDowngrade() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "shouldMarkFallbackAsStressDowngrade",
                StressRegime.class,
                WeatherProfile.class,
                boolean.class,
                boolean.class,
                double.class,
                double.class,
                double.class);
        method.setAccessible(true);

        boolean heavyRainDowngraded = (boolean) method.invoke(
                agent,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN,
                false,
                false,
                1.4,
                0.79,
                0.22);
        boolean severeStressDowngraded = (boolean) method.invoke(
                agent,
                StressRegime.SEVERE_STRESS,
                WeatherProfile.CLEAR,
                true,
                false,
                1.1,
                0.83,
                0.16);

        assertTrue(heavyRainDowngraded,
                "Harsh-weather fallback should remain classified as a stress downgrade");
        assertTrue(severeStressDowngraded,
                "Severe-stress fallback should remain classified as a stress downgrade");
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
    void heavyRainExecutionGateRejectsLongSingleButKeepsShortLocalRescue() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-RAIN-EXEC",
                "Rain Execution Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan longSingle = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("RAIN-LONG", List.of(order("RL-1", 0)), 42000.0, 1),
                List.of());
        longSingle.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);
        longSingle.setServiceTier("instant");
        longSingle.setPredictedDeadheadKm(1.7);
        longSingle.setOnTimeProbability(0.74);
        longSingle.setExpectedPostCompletionEmptyKm(1.6);
        longSingle.setExecutionScore(0.71);

        DispatchPlan shortSingle = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("RAIN-SHORT", List.of(order("RS-1", 10)), 42000.0, 1),
                List.of());
        shortSingle.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);
        shortSingle.setServiceTier("instant");
        shortSingle.setPredictedDeadheadKm(0.9);
        shortSingle.setOnTimeProbability(0.79);
        shortSingle.setExpectedPostCompletionEmptyKm(1.1);
        shortSingle.setExecutionScore(0.74);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "passesExecutionGate",
                DispatchPlan.class,
                StressRegime.class,
                WeatherProfile.class);
        method.setAccessible(true);

        boolean longAccepted = (boolean) method.invoke(
                agent,
                longSingle,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN);
        boolean shortAccepted = (boolean) method.invoke(
                agent,
                shortSingle,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN);

        assertFalse(longAccepted,
                "Heavy-rain execution should reject long single-order rescues that still look too expensive");
        assertTrue(shortAccepted,
                "Heavy-rain execution should keep short local rescues that remain feasible");
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

    @Test
    void heavyRainBorrowedShortlistNeedsRealShortageSignal() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-BORROWED-SHORTLIST",
                "Borrowed Shortlist Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "allowBorrowedShortlist",
                DriverDecisionContext.class,
                WeatherProfile.class,
                double.class);
        method.setAccessible(true);

        boolean moderateHeavyRainAllowed = (boolean) method.invoke(
                agent,
                moderateHeavyRainContext(driver),
                WeatherProfile.HEAVY_RAIN,
                0.45);
        boolean severeShortageAllowed = (boolean) method.invoke(
                agent,
                severeHeavyRainContext(driver),
                WeatherProfile.HEAVY_RAIN,
                0.45);

        assertFalse(moderateHeavyRainAllowed,
                "Heavy-rain borrowed shortlist should stay closed when local world is not in real shortage");
        assertTrue(severeShortageAllowed,
                "Heavy-rain borrowed shortlist should reopen when shortage is materially high");
    }

    @Test
    void heavyRainBatchAdmissionRejectsLooseThreeWaveButKeepsCompactRescue() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-BATCH-RAIN",
                "Batch Rain Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan looseThreeWave = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle(
                        "LOOSE-3",
                        List.of(order("L3-1", 0), order("L3-2", 10), order("L3-3", 20)),
                        128000.0,
                        3),
                List.of());
        looseThreeWave.setLastDropLandingScore(0.24);
        looseThreeWave.setPostDropDemandProbability(0.26);
        looseThreeWave.setDeliveryCorridorScore(0.32);
        looseThreeWave.setPredictedDeadheadKm(2.3);
        looseThreeWave.setExpectedPostCompletionEmptyKm(1.8);
        looseThreeWave.setBorrowedDependencyScore(0.24);
        looseThreeWave.setBatchValueScore(0.30);

        DispatchPlan compactThreeWave = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle(
                        "COMPACT-3",
                        List.of(order("C3-1", 30), order("C3-2", 40), order("C3-3", 50)),
                        132000.0,
                        3),
                List.of());
        compactThreeWave.setLastDropLandingScore(0.38);
        compactThreeWave.setPostDropDemandProbability(0.40);
        compactThreeWave.setDeliveryCorridorScore(0.46);
        compactThreeWave.setPredictedDeadheadKm(1.7);
        compactThreeWave.setExpectedPostCompletionEmptyKm(1.2);
        compactThreeWave.setBorrowedDependencyScore(0.08);
        compactThreeWave.setBatchValueScore(0.54);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "passesAiBatchAdmissionGate",
                DispatchPlan.class,
                StressRegime.class,
                WeatherProfile.class);
        method.setAccessible(true);

        boolean looseAccepted = (boolean) method.invoke(
                agent,
                looseThreeWave,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN);
        boolean compactAccepted = (boolean) method.invoke(
                agent,
                compactThreeWave,
                StressRegime.STRESS,
                WeatherProfile.HEAVY_RAIN);

        assertFalse(looseAccepted,
                "Heavy-rain batch admission should reject loose 3-order waves that do not land cleanly");
        assertTrue(compactAccepted,
                "Heavy-rain batch admission should keep compact 3-order waves that still finish cleanly");
    }

    @Test
    void heavyRainFallbackCoverageIsNotAlwaysOpen() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "allowFallbackCoverage",
                double.class,
                double.class,
                WeatherProfile.class,
                int.class,
                int.class);
        method.setAccessible(true);

        boolean moderateDemandAllowed = (boolean) method.invoke(
                agent,
                0.30,
                0.42,
                WeatherProfile.HEAVY_RAIN,
                5,
                5);
        boolean severeDemandAllowed = (boolean) method.invoke(
                agent,
                0.56,
                0.64,
                WeatherProfile.HEAVY_RAIN,
                9,
                5);

        assertFalse(moderateDemandAllowed,
                "Heavy-rain fallback should not be open by default when demand is still manageable");
        assertTrue(severeDemandAllowed,
                "Heavy-rain fallback should reopen when shortage and traffic make rescue unavoidable");
    }

    @Test
    void cleanStressDoesNotAlwaysOpenFallbackSlot() throws Exception {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(List.of());
        Driver driver = new Driver(
                "D-CLEAN-FALLBACK",
                "Clean Fallback Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        DispatchPlan fallback = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("CLEAN-FALLBACK", List.of(order("CF-1", 0)), 42000.0, 1),
                List.of());
        fallback.setSelectionBucket(SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD);
        fallback.setStressFallbackOnly(true);
        fallback.setOnTimeProbability(0.80);
        fallback.setPredictedDeadheadKm(1.9);
        fallback.setLateRisk(0.18);
        fallback.setStressRescueScore(0.46);

        Method method = OmegaDispatchAgent.class.getDeclaredMethod(
                "shouldAllowFallbackSlot",
                DriverDecisionContext.class,
                DispatchPlan.class,
                boolean.class,
                boolean.class);
        method.setAccessible(true);

        boolean relaxedCleanStressAllowed = (boolean) method.invoke(
                agent,
                cleanStressContext(driver),
                fallback,
                true,
                false);
        boolean realShortageAllowed = (boolean) method.invoke(
                agent,
                cleanShortageContext(driver),
                fallback,
                false,
                false);

        assertFalse(relaxedCleanStressAllowed,
                "Clean-regime stress should not auto-open fallback when local batching is still viable");
        assertTrue(realShortageAllowed,
                "Fallback slot should reopen when clean-regime shortage is materially real");
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

    private static DriverDecisionContext cleanStressContext(Driver driver) {
        return new DriverDecisionContext(
                driver,
                List.of(order("CS-1", 0), order("CS-2", 10), order("CS-3", 20)),
                List.of(),
                0.50,
                1.1,
                1.2,
                1.3,
                1.3,
                1.2,
                0.42,
                0.46,
                0.08,
                0.26,
                0.22,
                0.48,
                0.34,
                0.20,
                0.10,
                0.26,
                0.28,
                0.18,
                0.44,
                0.24,
                3.4,
                2,
                4.8,
                1,
                1,
                3,
                false,
                0.64,
                2.2,
                0.30,
                0.42,
                0.28,
                List.of(),
                List.of(),
                StressRegime.STRESS);
    }

    private static DriverDecisionContext cleanShortageContext(Driver driver) {
        return new DriverDecisionContext(
                driver,
                List.of(order("CQ-1", 0)),
                List.of(),
                0.54,
                1.0,
                0.8,
                0.8,
                0.7,
                0.7,
                0.46,
                0.52,
                0.10,
                0.24,
                0.18,
                0.44,
                0.52,
                0.22,
                0.16,
                0.30,
                0.32,
                0.12,
                0.68,
                0.18,
                2.4,
                0,
                1.8,
                0,
                0,
                1,
                false,
                0.20,
                0.8,
                0.12,
                0.16,
                0.54,
                List.of(),
                List.of(),
                StressRegime.STRESS);
    }

    private static DriverDecisionContext moderateHeavyRainContext(Driver driver) {
        return new DriverDecisionContext(
                driver,
                List.of(order("MH-1", 0), order("MH-2", 10)),
                List.of(),
                0.45,
                1.1,
                1.0,
                1.0,
                0.9,
                0.8,
                0.50,
                0.54,
                0.78,
                0.32,
                0.30,
                0.42,
                0.30,
                0.22,
                0.18,
                0.40,
                0.36,
                0.18,
                0.58,
                0.2,
                3.0,
                1,
                4.0,
                1,
                0,
                2,
                true,
                0.32,
                1.2,
                0.20,
                0.12,
                0.42,
                List.of(),
                List.of(),
                StressRegime.STRESS);
    }

    private static DriverDecisionContext severeHeavyRainContext(Driver driver) {
        return new DriverDecisionContext(
                driver,
                List.of(order("SH-1", 0)),
                List.of(),
                0.45,
                1.2,
                0.9,
                0.8,
                0.8,
                0.7,
                0.54,
                0.58,
                0.82,
                0.72,
                0.64,
                0.18,
                0.78,
                0.28,
                0.20,
                0.76,
                0.80,
                0.10,
                0.92,
                0.2,
                3.5,
                0,
                2.0,
                0,
                0,
                1,
                true,
                0.18,
                0.8,
                0.16,
                0.08,
                0.84,
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
