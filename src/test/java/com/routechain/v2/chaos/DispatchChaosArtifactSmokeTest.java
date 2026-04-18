package com.routechain.v2.chaos;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchChaosArtifactSmokeTest {
    private final DispatchChaosBenchmarkHarness harness = new DispatchChaosBenchmarkHarness();

    @Test
    void writesChaosArtifactsForRequestedCell() throws Exception {
        Path outputDirectory = Path.of(value("dispatchChaos.outputDir", "DISPATCH_CHAOS_OUTPUT_DIR", "build/dispatch-chaos-smoke"));
        DispatchChaosRunResult result = harness.run(new DispatchChaosBenchmarkHarness.ChaosRequest(
                DispatchPhase3Support.ChaosFaultType.fromWire(value("dispatchChaos.fault", "DISPATCH_CHAOS_FAULT", "tabular-unavailable")),
                DispatchPerfBenchmarkHarness.WorkloadSize.valueOf(value("dispatchChaos.size", "DISPATCH_CHAOS_SIZE", "M")),
                DispatchPhase3Support.ScenarioPack.fromWire(value("dispatchChaos.scenarioPack", "DISPATCH_CHAOS_SCENARIO_PACK", "normal-clear")),
                DispatchPhase3Support.ExecutionMode.fromWire(value("dispatchChaos.executionMode", "DISPATCH_CHAOS_EXECUTION_MODE", "controlled")),
                value("dispatchChaos.machineLabel", "DISPATCH_CHAOS_MACHINE_LABEL", DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL),
                outputDirectory));

        DispatchStabilityArtifactWriter.ArtifactPaths artifacts = DispatchStabilityArtifactWriter.writeChaosResult(result, outputDirectory);
        assertTrue(artifacts.jsonPath().toFile().isFile());
        assertTrue(artifacts.markdownPath().toFile().isFile());
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
