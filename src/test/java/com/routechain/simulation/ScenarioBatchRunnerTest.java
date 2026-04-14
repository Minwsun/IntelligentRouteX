package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioBatchRunnerTest {

    @Test
    void certificationLaneIncludesLightRainCoverageScenario() {
        List<String> scenarios = ScenarioBatchRunner.listResolvedScenarioNamesForLane("certification");

        assertTrue(scenarios.contains("instant-rain_onset"),
                "Certification lane must include the LIGHT_RAIN recovery sample");
    }

    @Test
    void nightlyLaneIncludesExpandedRecoveryScenarios() {
        List<String> scenarios = ScenarioBatchRunner.listResolvedScenarioNamesForLane("nightly");

        assertTrue(scenarios.contains("instant-rain_onset"),
                "Nightly lane must keep the LIGHT_RAIN recovery sample");
        assertTrue(scenarios.contains("merchant_cluster"),
                "Nightly lane must keep merchant-cluster demand-spike coverage");
        assertTrue(scenarios.contains("storm"),
                "Nightly lane must keep the storm stress sample");
    }
}
