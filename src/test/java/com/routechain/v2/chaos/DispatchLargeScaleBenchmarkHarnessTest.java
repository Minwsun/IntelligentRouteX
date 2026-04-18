package com.routechain.v2.chaos;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchLargeScaleBenchmarkHarnessTest {
    private final DispatchLargeScaleBenchmarkHarness harness = new DispatchLargeScaleBenchmarkHarness();

    @TempDir
    Path tempDir;

    @Test
    void largeScaleHarnessRunsControlledMScenarioAcrossABAndC() {
        List<DispatchLargeScaleBenchmarkResult> results = harness.run(new DispatchLargeScaleBenchmarkHarness.BenchmarkRequest(
                List.of(
                        DispatchPerfBenchmarkHarness.BaselineId.A,
                        DispatchPerfBenchmarkHarness.BaselineId.B,
                        DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                1,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(result -> result.stageLatencyStats().size() == 12));
        assertTrue(results.stream().allMatch(result -> result.qualitySummary().conflictFreeAssignments()));
    }

    @Test
    void xlCanBeSerializedAsDeferred() {
        DispatchLargeScaleBenchmarkResult result = harness.run(new DispatchLargeScaleBenchmarkHarness.BenchmarkRequest(
                List.of(DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.XL,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                1,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir)).getFirst();

        assertTrue(result.deferred());
        assertTrue(result.notes().contains("deferred-on-current-machine"));
    }
}
