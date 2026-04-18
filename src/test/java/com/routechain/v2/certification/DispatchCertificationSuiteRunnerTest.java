package com.routechain.v2.certification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchCertificationSuiteRunnerTest {
    private final DispatchCertificationSuiteRunner runner = new DispatchCertificationSuiteRunner();

    @TempDir
    Path tempDir;

    @Test
    void suiteRunnerAggregatesWeatherTrafficPackAndCarriesLiveSourceMetadata() {
        DispatchCertificationSuiteReport report = runner.weatherTrafficPack(tempDir.resolve("weather-traffic"));

        assertEquals("dispatch-certification-suite-report/v1", report.schemaVersion());
        assertEquals("weather-traffic-realism", report.suiteName());
        assertEquals(5, report.scenarioCount());
        assertEquals(5, report.passedScenarioCount());
        assertEquals(0, report.failedScenarioCount());
        assertTrue(report.failureSummaries().isEmpty());
        assertTrue(report.scenarioReports().stream().allMatch(scenario -> scenario.decisionStages().size() == 12));
        assertTrue(report.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("stale-weather"))
                .flatMap(scenario -> scenario.degradeReasons().stream())
                .anyMatch("open-meteo-stale"::equals));
        assertTrue(report.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("traffic-shock"))
                .flatMap(scenario -> scenario.liveMetadataSources().stream())
                .anyMatch("tomtom-traffic"::equals));
    }

    @Test
    void forecastAndWorkerPacksPreserveMetadataAndOptionalFallbacks() {
        DispatchCertificationSuiteReport forecastReport = runner.forecastPack(tempDir.resolve("forecast"));
        DispatchCertificationSuiteReport workerReport = runner.workerDegradationPack(tempDir.resolve("workers"));

        assertEquals(5, forecastReport.scenarioCount());
        assertEquals(5, forecastReport.passedScenarioCount());
        assertTrue(forecastReport.scenarioReports().stream()
                .filter(scenario -> Set.of("zone-burst-strong", "demand-shift-strong", "post-drop-shift-strong").contains(scenario.scenarioName()))
                .allMatch(scenario -> scenario.mlMetadataSources().contains("chronos-2")));

        assertEquals(5, workerReport.scenarioCount());
        assertEquals(5, workerReport.passedScenarioCount());
        assertTrue(workerReport.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("worker-ready-false-optional-path"))
                .flatMap(scenario -> scenario.degradeReasons().stream())
                .anyMatch("worker-ready-false-optional-path"::equals));
    }

    @Test
    void selectorExecutorPackKeepsConflictSafetyAcrossAllScenarios() {
        DispatchCertificationSuiteReport report = runner.selectorExecutorPack(tempDir.resolve("selector-executor"));

        assertEquals(4, report.scenarioCount());
        assertEquals(4, report.passedScenarioCount());
        assertTrue(report.scenarioReports().stream().allMatch(DispatchCertificationScenarioReport::conflictFreeAssignments));
        assertTrue(report.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("executor-defensive-skip"))
                .flatMap(scenario -> scenario.degradeReasons().stream())
                .anyMatch("executor-conflict-validation-failed"::equals));
    }

    @Test
    void bootPersistencePackKeepsWarmBootReplayIsolationAndHotReuseEvidence() {
        DispatchCertificationSuiteReport report = runner.bootPersistencePack(tempDir.resolve("boot-persistence"));
        assertEquals(4, report.scenarioCount());
        assertEquals(4, report.passedScenarioCount());
        assertTrue(report.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("warm-boot-across-restart"))
                .allMatch(DispatchCertificationScenarioReport::passed));
        assertTrue(report.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("repeated-hot-start-family"))
                .allMatch(scenario -> !scenario.reusedStageNames().isEmpty() && scenario.estimatedSavedMs() >= 0L));
        assertTrue(report.scenarioReports().stream()
                .filter(scenario -> scenario.scenarioName().equals("replay-safe-under-certification-load"))
                .allMatch(DispatchCertificationScenarioReport::passed));
    }
}
