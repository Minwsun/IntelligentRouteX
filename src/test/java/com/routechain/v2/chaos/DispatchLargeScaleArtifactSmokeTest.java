package com.routechain.v2.chaos;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchLargeScaleArtifactSmokeTest {
    private final DispatchLargeScaleBenchmarkHarness harness = new DispatchLargeScaleBenchmarkHarness();

    @Test
    void writesLargeScaleArtifactsForRequestedCell() throws Exception {
        Path outputDirectory = Path.of(value("dispatchLargeScale.outputDir", "DISPATCH_LARGE_SCALE_OUTPUT_DIR", "build/dispatch-large-scale-smoke"));
        List<DispatchLargeScaleBenchmarkResult> results = harness.run(new DispatchLargeScaleBenchmarkHarness.BenchmarkRequest(
                List.of(DispatchPerfBenchmarkHarness.BaselineId.valueOf(value("dispatchLargeScale.baseline", "DISPATCH_LARGE_SCALE_BASELINE", "C"))),
                DispatchPerfBenchmarkHarness.WorkloadSize.valueOf(value("dispatchLargeScale.size", "DISPATCH_LARGE_SCALE_SIZE", "M")),
                DispatchPhase3Support.ScenarioPack.fromWire(value("dispatchLargeScale.scenarioPack", "DISPATCH_LARGE_SCALE_SCENARIO_PACK", "normal-clear")),
                DispatchPhase3Support.ExecutionMode.fromWire(value("dispatchLargeScale.executionMode", "DISPATCH_LARGE_SCALE_EXECUTION_MODE", "controlled")),
                Integer.parseInt(value("dispatchLargeScale.runCount", "DISPATCH_LARGE_SCALE_RUN_COUNT", "1")),
                value("dispatchLargeScale.machineLabel", "DISPATCH_LARGE_SCALE_MACHINE_LABEL", DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL),
                Boolean.parseBoolean(value("dispatchLargeScale.runDeferredXl", "DISPATCH_LARGE_SCALE_RUN_DEFERRED_XL", "false")),
                outputDirectory));

        for (DispatchLargeScaleBenchmarkResult result : results) {
            DispatchStabilityArtifactWriter.ArtifactPaths artifacts = DispatchStabilityArtifactWriter.writeLargeScaleResult(result, outputDirectory);
            assertTrue(artifacts.jsonPath().toFile().isFile());
            assertTrue(artifacts.markdownPath().toFile().isFile());
        }
        assertFalse(DispatchStabilityArtifactWriter.jsonArtifacts(outputDirectory).isEmpty());
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
