package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import org.junit.jupiter.api.Test;

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
                List.of(),
                List.of(),
                0.42,
                1.1,
                1.4,
                1.5,
                1.5,
                1.3,
                0.24,
                0.3,
                0.2,
                0.10,
                0.18,
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
                0.42,
                0.3,
                0.2,
                0.78,
                0.82,
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
}
