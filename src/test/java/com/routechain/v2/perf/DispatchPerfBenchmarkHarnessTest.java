package com.routechain.v2.perf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchPerfBenchmarkHarnessTest {
    private final DispatchPerfBenchmarkHarness harness = new DispatchPerfBenchmarkHarness();

    @TempDir
    Path tempDir;

    @Test
    void coldWarmAndHotRunsPreserveTwelveStages() {
        DispatchPerfBenchmarkResult coldResult = harness.run(new DispatchPerfBenchmarkHarness.BenchmarkRequest(
                DispatchPerfBenchmarkHarness.BaselineId.A,
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchPerfBenchmarkHarness.RunMode.COLD,
                0,
                1,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));
        DispatchPerfBenchmarkResult warmResult = harness.run(new DispatchPerfBenchmarkHarness.BenchmarkRequest(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchPerfBenchmarkHarness.RunMode.WARM,
                0,
                1,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));
        DispatchPerfBenchmarkResult hotResult = harness.run(new DispatchPerfBenchmarkHarness.BenchmarkRequest(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchPerfBenchmarkHarness.RunMode.HOT,
                0,
                1,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));

        assertEquals(12, coldResult.stageLatencyStats().size());
        assertEquals(12, warmResult.stageLatencyStats().size());
        assertEquals(12, hotResult.stageLatencyStats().size());
        assertTrue(warmResult.notes().contains("warm-boot-recoverability-observed"));
        assertTrue(hotResult.estimatedSavedMsStats().p50Ms() >= 0L);
    }

    @Test
    void hotRunRecordsReusedStagesForCompatibleFullBaseline() {
        DispatchPerfBenchmarkResult result = harness.run(new DispatchPerfBenchmarkHarness.BenchmarkRequest(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchPerfBenchmarkHarness.RunMode.HOT,
                0,
                2,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));

        assertFalse(result.deferred());
        assertEquals(2, result.totalDispatchCount());
        assertTrue(result.totalLatencyStats().p95Ms() >= result.totalLatencyStats().p50Ms());
        assertTrue(result.reusedStageNames().keySet().stream()
                .allMatch(stage -> List.of("pair-graph", "micro-cluster", "bundle-pool", "route-proposal-pool").contains(stage)));
        assertTrue(result.estimatedSavedMsStats().p50Ms() >= 0L);
    }

    @Test
    void xlRunCanBeExplicitlyDeferred() {
        DispatchPerfBenchmarkResult result = harness.run(new DispatchPerfBenchmarkHarness.BenchmarkRequest(
                DispatchPerfBenchmarkHarness.BaselineId.C,
                DispatchPerfBenchmarkHarness.WorkloadSize.XL,
                DispatchPerfBenchmarkHarness.RunMode.COLD,
                0,
                1,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                tempDir));

        assertTrue(result.deferred());
        assertTrue(result.notes().contains("deferred-on-current-machine"));
        assertEquals(0, result.totalDispatchCount());
    }
}
