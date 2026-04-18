package com.routechain.v2.benchmark;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchQualityBenchmarkHarnessTest {
    private final DispatchQualityBenchmarkHarness harness = new DispatchQualityBenchmarkHarness();

    @TempDir
    Path tempDir;

    @Test
    void benchmarkRunEmitsThreeBaselinesAndOneComparisonReport() {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(
                        DispatchPerfBenchmarkHarness.BaselineId.A,
                        DispatchPerfBenchmarkHarness.BaselineId.B,
                        DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));

        assertEquals(3, run.rawResults().size());
        assertNotNull(run.comparisonReport());
        assertTrue(run.rawResults().stream().allMatch(result -> result.decisionStages().size() == 12));
        assertTrue(run.rawResults().stream().allMatch(result -> !result.deferred()));
    }

    @Test
    void stableScenarioKeepsConflictFreeAssignmentsAndPopulatesMetrics() {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(
                        DispatchPerfBenchmarkHarness.BaselineId.A,
                        DispatchPerfBenchmarkHarness.BaselineId.B,
                        DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));

        DispatchQualityBenchmarkResult fullV2 = run.rawResults().stream()
                .filter(result -> result.baselineId().equals("C"))
                .findFirst()
                .orElseThrow();
        assertTrue(fullV2.metrics().conflictFreeAssignments());
        assertTrue(fullV2.metrics().robustUtilityAverage() >= 0.0);
        assertTrue(fullV2.metrics().selectorObjectiveValue() >= 0.0);
        assertTrue(fullV2.metrics().workerFallbackRate() >= 0.0);
        assertTrue(fullV2.metrics().liveSourceFallbackRate() >= 0.0);
    }

    @Test
    void ablationProducesDeltaReportWithoutChangingContracts() {
        DispatchAblationResult result = harness.ablate(new DispatchQualityBenchmarkHarness.AblationRequest(
                DispatchQualityBenchmarkHarness.AblationComponent.ORTOOLS,
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                false,
                tempDir));

        assertEquals("ortools", result.toggledComponent());
        assertFalse(result.deltaSummary().isEmpty());
        assertTrue(result.controlMetrics().conflictFreeAssignments());
        assertTrue(result.variantMetrics().conflictFreeAssignments());
    }
}
