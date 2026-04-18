package com.routechain.v2.benchmark;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchQualityArtifactSmokeTest {
    private final DispatchQualityBenchmarkHarness harness = new DispatchQualityBenchmarkHarness();

    @Test
    void writesQualityBenchmarkArtifactsForRequestedCell() throws Exception {
        List<DispatchPerfBenchmarkHarness.BaselineId> baselines = parseBaselines(value("dispatchQuality.baselines", "DISPATCH_QUALITY_BASELINES", "A,B,C"));
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                baselines,
                DispatchPerfBenchmarkHarness.WorkloadSize.valueOf(value("dispatchQuality.size", "DISPATCH_QUALITY_SIZE", "S")),
                DispatchQualityBenchmarkHarness.ScenarioPack.fromWire(value("dispatchQuality.scenarioPack", "DISPATCH_QUALITY_SCENARIO_PACK", "normal-clear")),
                DispatchQualityBenchmarkHarness.ExecutionMode.valueOf(value("dispatchQuality.executionMode", "DISPATCH_QUALITY_EXECUTION_MODE", "CONTROLLED").toUpperCase().replace('-', '_')),
                value("dispatchQuality.machineLabel", "DISPATCH_QUALITY_MACHINE_LABEL", DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL),
                Boolean.parseBoolean(value("dispatchQuality.runDeferredXl", "DISPATCH_QUALITY_RUN_DEFERRED_XL", "false")),
                Path.of(value("dispatchQuality.outputDir", "DISPATCH_QUALITY_OUTPUT_DIR", "build/dispatch-quality-smoke"))));

        DispatchQualityArtifactWriter.BenchmarkArtifacts artifacts = DispatchQualityArtifactWriter.writeBenchmarkRun(
                run,
                Path.of(value("dispatchQuality.outputDir", "DISPATCH_QUALITY_OUTPUT_DIR", "build/dispatch-quality-smoke")));

        assertFalse(artifacts.rawJsonPaths().isEmpty());
        assertTrue(artifacts.rawJsonPaths().stream().allMatch(path -> path.toFile().isFile()));
        if (run.comparisonReport() != null) {
            assertNotNull(artifacts.comparisonJsonPath());
            assertTrue(artifacts.comparisonJsonPath().toFile().isFile());
            assertTrue(artifacts.comparisonCsvPath().toFile().isFile());
        }
    }

    private List<DispatchPerfBenchmarkHarness.BaselineId> parseBaselines(String value) {
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(DispatchPerfBenchmarkHarness.BaselineId::valueOf)
                .toList();
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
