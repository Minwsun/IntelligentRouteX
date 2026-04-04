package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAiCertificationRunnerTest {

    @Test
    void shouldPassAbsoluteSmokeGateEvenWhenLegacyReferenceIsLower() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        RunReport legacy = createReport(
                "legacy-run",
                "counterfactual-normal-Legacy-seed42",
                25.0,
                90.0,
                52.0,
                24.0,
                1.10,
                99.0,
                0.80,
                1.2,
                0.15,
                0.80,
                0.0,
                0.0,
                0.0,
                8.0,
                12.0
        );
        RunReport omega = createReport(
                "omega-run",
                "counterfactual-normal-Omega-current-seed42",
                20.0,
                87.0,
                60.0,
                30.0,
                1.40,
                97.0,
                0.75,
                1.5,
                0.22,
                0.95,
                100.0,
                35.0,
                0.0,
                6.0,
                9.0
        );

        BenchmarkArtifactWriter.writeRun(legacy);
        BenchmarkArtifactWriter.writeRun(omega);
        BenchmarkArtifactWriter.writeCompare(ReplayCompareResult.compare(legacy, omega));
        BenchmarkArtifactWriter.writeRuntimeSloSummary(new RuntimeSloSummary(
                "local-production-small-smoke",
                6.0,
                9.0,
                true,
                true,
                true,
                true,
                List.of()
        ));

        assertDoesNotThrow(() -> RouteAiCertificationRunner.main(new String[]{"smoke"}));

        String summaryJson = Files.readString(root.resolve("certification").resolve("route-ai-certification-smoke.json"));
        assertTrue(summaryJson.contains("\"overallPass\": true"));
        assertTrue(summaryJson.contains("warning only"));
    }

    private void deleteRecursively(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to clean benchmark artifact root", e);
                        }
                    });
        }
    }

    private RunReport createReport(String runId,
                                   String scenarioName,
                                   double completionRate,
                                   double onTimeRate,
                                   double cancellationRate,
                                   double deadheadRatio,
                                   double deadheadPerCompleted,
                                   double postDropHitRate,
                                   double corridorQuality,
                                   double expectedEmptyKm,
                                   double zigZagPenalty,
                                   double avgAssignedDeadheadKm,
                                   double fallbackShare,
                                   double borrowedShare,
                                   double stressDowngradeRate,
                                   double dispatchP95Ms,
                                   double dispatchP99Ms) {
        Instant now = Instant.parse("2026-04-04T00:00:00Z");
        return new RunReport(
                runId,
                scenarioName,
                42L,
                now,
                now.plusSeconds(1800),
                900L,
                100,
                40,
                completionRate,
                onTimeRate,
                cancellationRate,
                0.0,
                deadheadRatio,
                20.0,
                0.82,
                1.5,
                42000.0,
                5.0,
                72.0,
                2.0,
                3,
                8.0,
                0,
                180000.0,
                0.72,
                4.5,
                0,
                0,
                26000.0,
                0.0,
                0.8,
                expectedEmptyKm,
                12.0,
                corridorQuality,
                zigZagPenalty,
                100.0,
                0.0,
                0.0,
                0.0,
                stressDowngradeRate,
                0.0,
                0.0,
                avgAssignedDeadheadKm,
                deadheadPerCompleted,
                1.10,
                postDropHitRate,
                borrowedShare,
                fallbackShare,
                0.0,
                DispatchStageBreakdown.empty(),
                new LatencyBreakdown(6.0, 5.0, dispatchP95Ms, dispatchP99Ms, 2.0, 3.0, 3.0, 4.0, 1200.0, 2100.0, 8.5, 12, 10),
                new IntelligenceScorecard(0.72, 0.68, 0.64, 0.61, 0.66, 0.63, 0.58, 1.0, 0.74, 0.69, 0.54, 0.21, 0.82, 0.13, "STRONG", "PASSING"),
                new ScenarioAcceptanceResult(scenarioName, "instant", "local-production-small-50", true, true, true, true, true, "STRONG", "PASSING", ""),
                "instant",
                Map.of("instant", new ServiceTierMetrics("instant", 100, 84, 84.0, 45.0, 28000.0)),
                new ForecastCalibrationSummary(4.5, 3.1, -0.05, 0.42),
                DispatchRecoveryDecomposition.empty()
        );
    }
}
