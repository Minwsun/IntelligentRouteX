package com.routechain.v2.benchmark;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchAblationArtifactSmokeTest {
    private final DispatchQualityBenchmarkHarness harness = new DispatchQualityBenchmarkHarness();

    @Test
    void writesAblationArtifactsForRequestedCell() throws Exception {
        Path outputDirectory = Path.of(value("dispatchAblation.outputDir", "DISPATCH_ABLATION_OUTPUT_DIR", "build/dispatch-ablation-smoke"));
        DispatchAblationResult result = harness.ablate(new DispatchQualityBenchmarkHarness.AblationRequest(
                DispatchQualityBenchmarkHarness.AblationComponent.fromWire(value("dispatchAblation.component", "DISPATCH_ABLATION_COMPONENT", "tabular")),
                DispatchPerfBenchmarkHarness.WorkloadSize.valueOf(value("dispatchAblation.size", "DISPATCH_ABLATION_SIZE", "S")),
                DispatchQualityBenchmarkHarness.ScenarioPack.fromWire(value("dispatchAblation.scenarioPack", "DISPATCH_ABLATION_SCENARIO_PACK", "normal-clear")),
                DispatchQualityBenchmarkHarness.ExecutionMode.valueOf(value("dispatchAblation.executionMode", "DISPATCH_ABLATION_EXECUTION_MODE", "CONTROLLED").toUpperCase().replace('-', '_')),
                Boolean.parseBoolean(value("dispatchAblation.runDeferredXl", "DISPATCH_ABLATION_RUN_DEFERRED_XL", "false")),
                outputDirectory));

        DispatchQualityArtifactWriter.AblationArtifacts artifacts = DispatchQualityArtifactWriter.writeAblationResult(result, outputDirectory);
        assertTrue(artifacts.jsonPath().toFile().isFile());
        assertTrue(artifacts.markdownPath().toFile().isFile());
        assertTrue(artifacts.csvPath().toFile().isFile());
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
