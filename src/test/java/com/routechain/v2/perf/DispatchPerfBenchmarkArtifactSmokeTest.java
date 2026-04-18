package com.routechain.v2.perf;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchPerfBenchmarkArtifactSmokeTest {
    private final DispatchPerfBenchmarkHarness harness = new DispatchPerfBenchmarkHarness();

    @Test
    void writesBenchmarkArtifactsForRequestedMatrixCell() throws Exception {
        DispatchPerfBenchmarkHarness.BaselineId baselineId = DispatchPerfBenchmarkHarness.BaselineId.valueOf(
                value("dispatchPerf.baseline", "DISPATCH_PERF_BASELINE", "A"));
        DispatchPerfBenchmarkHarness.WorkloadSize workloadSize = DispatchPerfBenchmarkHarness.WorkloadSize.valueOf(
                value("dispatchPerf.size", "DISPATCH_PERF_SIZE", "S"));
        DispatchPerfBenchmarkHarness.RunMode runMode = DispatchPerfBenchmarkHarness.RunMode.valueOf(
                value("dispatchPerf.mode", "DISPATCH_PERF_MODE", "COLD").toUpperCase());
        Path outputDirectory = Path.of(value("dispatchPerf.outputDir", "DISPATCH_PERF_OUTPUT_DIR", "build/dispatch-perf-smoke"));
        boolean runDeferredXl = Boolean.parseBoolean(value("dispatchPerf.runDeferredXl", "DISPATCH_PERF_RUN_DEFERRED_XL", "false"));

        DispatchPerfBenchmarkResult result = harness.run(new DispatchPerfBenchmarkHarness.BenchmarkRequest(
                baselineId,
                workloadSize,
                runMode,
                Integer.parseInt(value("dispatchPerf.warmupRuns", "DISPATCH_PERF_WARMUP_RUNS", "0")),
                Integer.parseInt(value("dispatchPerf.measuredRuns", "DISPATCH_PERF_MEASURED_RUNS", "1")),
                value("dispatchPerf.machineLabel", "DISPATCH_PERF_MACHINE_LABEL", DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL),
                runDeferredXl,
                outputDirectory.resolve("feedback")));

        DispatchPerfArtifactWriter.ArtifactPaths artifactPaths = DispatchPerfArtifactWriter.writeResult(result, outputDirectory);
        DispatchPerfBenchmarkResult reloaded = DispatchPerfArtifactWriter.readResult(artifactPaths.jsonPath());

        assertTrue(artifactPaths.jsonPath().toFile().isFile());
        assertTrue(artifactPaths.markdownPath().toFile().isFile());
        assertEquals(result.baselineId(), reloaded.baselineId());
        assertEquals(result.workloadSize(), reloaded.workloadSize());
        assertEquals(result.runMode(), reloaded.runMode());
        assertEquals(result.totalLatencyStats().p50Ms(), reloaded.totalLatencyStats().p50Ms());
    }

    private String value(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }
}
