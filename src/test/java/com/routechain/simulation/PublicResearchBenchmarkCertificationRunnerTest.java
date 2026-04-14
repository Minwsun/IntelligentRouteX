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

class PublicResearchBenchmarkCertificationRunnerTest {

    @Test
    void shouldWritePublicResearchSummaryWhenFamiliesArePresent() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        writeResearchFamily("solomon", "distributed_customers");
        writeResearchFamily("homberger", "large_scale_timewindow");
        writeResearchFamily("li-lim-pdptw", "pickup_delivery_mixed");

        assertDoesNotThrow(() -> PublicResearchBenchmarkCertificationRunner.main(new String[]{"certification"}));

        Path json = root.resolve("certification").resolve("public-research-benchmark-certification.json");
        Path markdown = root.resolve("certification").resolve("public-research-benchmark-certification.md");
        assertTrue(Files.exists(json));
        assertTrue(Files.exists(markdown));
        String payload = Files.readString(json);
        assertTrue(payload.contains("\"overallPass\": true"));
        assertTrue(payload.contains("\"familyId\": \"li-lim-pdptw\""));
    }

    @Test
    void shouldExplainDatasetBlockerWhenResearchFamiliesAreEmpty() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        writeResearchFamily("solomon", "distributed_customers");

        try {
            PublicResearchBenchmarkCertificationRunner.main(new String[]{"certification"});
        } catch (IllegalStateException ignored) {
            // Expected because benchmark coverage is incomplete.
        }

        Path json = root.resolve("certification").resolve("public-research-benchmark-certification.json");
        String payload = Files.readString(json);
        assertTrue(payload.contains("dataset directory is empty for family homberger"),
                "Certification summary should call out missing public datasets explicitly");
        assertTrue(payload.contains("fetch_route_research_datasets.ps1"),
                "Certification summary should point to the dataset bootstrap script");
    }

    private void writeResearchFamily(String family, String scenarioName) {
        RunReport legacy = createReport(
                "B-" + family + "-case-s42-legacy-seed42",
                scenarioName + "-legacy",
                20.0,
                86.0,
                58.0,
                31.0,
                1.5,
                96.0,
                0.74,
                1.6,
                0.18,
                0.96
        );
        RunReport omega = createReport(
                "B-" + family + "-case-s42-omega-seed42",
                scenarioName + "-omega",
                21.0,
                87.0,
                57.0,
                30.0,
                1.4,
                97.0,
                0.75,
                1.5,
                0.17,
                0.95
        );
        BenchmarkArtifactWriter.writeRun(legacy);
        BenchmarkArtifactWriter.writeRun(omega);
        BenchmarkArtifactWriter.writeCompare(ReplayCompareResult.compare(legacy, omega));
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
                                   double avgAssignedDeadheadKm) {
        Instant now = Instant.parse("2026-04-10T00:00:00Z");
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
                0.0,
                0.0,
                0.0,
                avgAssignedDeadheadKm,
                deadheadPerCompleted,
                1.10,
                postDropHitRate,
                0.0,
                0.0,
                0.0,
                DispatchStageBreakdown.empty(),
                new LatencyBreakdown(6.0, 5.0, 8.0, 12.0, 2.0, 3.0, 3.0, 4.0, 1200.0, 2100.0, 8.5, 12, 10),
                new IntelligenceScorecard(0.72, 0.68, 0.64, 0.61, 0.66, 0.63, 0.58, 1.0, 0.74, 0.69, 0.54, 0.21, 0.82, 0.13, "STRONG", "PASSING"),
                new ScenarioAcceptanceResult(scenarioName, "instant", "local-production-small-50", true, true, true, true, true, "STRONG", "PASSING", ""),
                "instant",
                Map.of("instant", new ServiceTierMetrics("instant", 100, 84, 84.0, 45.0, 28000.0)),
                new ForecastCalibrationSummary(4.5, 3.1, -0.05, 0.42),
                DispatchRecoveryDecomposition.empty()
        );
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
}
