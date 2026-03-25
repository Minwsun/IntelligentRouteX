package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkArtifactWriterTest {

    @Test
    void shouldPersistRunAndCompareArtifacts() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        RunReport baseline = createReport("baseline-run", "scenario-a", 1.2, 24.0, 30.0, 2.6);
        RunReport candidate = createReport("candidate-run", "scenario-a", 3.8, 29.0, 55.0, 1.2);
        ReplayCompareResult compare = ReplayCompareResult.compare(baseline, candidate);

        BenchmarkArtifactWriter.writeRun(baseline);
        BenchmarkArtifactWriter.writeRun(candidate);
        BenchmarkArtifactWriter.writeCompare(compare);

        assertTrue(Files.exists(root.resolve("runs").resolve("baseline-run.json")));
        assertTrue(Files.exists(root.resolve("runs").resolve("candidate-run.json")));
        assertTrue(Files.exists(root.resolve("compares")
                .resolve("baseline-run__vs__candidate-run.json")));
        String runCsv = Files.readString(root.resolve("run_reports.csv"));
        String compareCsv = Files.readString(root.resolve("replay_compare_results.csv"));
        String runJson = Files.readString(root.resolve("runs").resolve("candidate-run.json"));
        String compareJson = Files.readString(root.resolve("compares")
                .resolve("baseline-run__vs__candidate-run.json"));
        assertTrue(runCsv.contains("baseline-run"));
        assertTrue(runCsv.contains("realAssign"));
        assertTrue(runCsv.contains("augment"));
        assertTrue(runCsv.contains("holdOnly"));
        assertTrue(compareCsv.contains("candidate-run"));
        assertTrue(runJson.contains("\"prePickupAugmentRate\""));
        assertTrue(runJson.contains("\"holdOnlySelectionRate\""));
        assertTrue(compareJson.contains("\"realAssignmentRateDelta\""));
        assertTrue(compareJson.contains("\"waveAssemblyWaitRateDelta\""));
        assertTrue(compareJson.contains("\"thirdOrderLaunchRateDelta\""));
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
                            throw new IllegalStateException("Failed to clean benchmark artifact test root", e);
                        }
                    });
        }
    }

    private RunReport createReport(String id,
                                   String scenario,
                                   double visibleBundleThreePlusRate,
                                   double deliveryCorridorQuality,
                                   double lastDropGoodZoneRate,
                                   double expectedEmptyKm) {
        Instant now = Instant.parse("2026-03-24T00:00:00Z");
        return new RunReport(
                id,
                scenario,
                42L,
                now,
                now.plusSeconds(1800),
                900L,
                100,
                40,
                22.0,
                87.0,
                10.0,
                0.0,
                30.0,
                20.0,
                0.82,
                1.5,
                42000.0,
                5.0,
                72.0,
                2.1,
                3,
                8.0,
                0,
                180000.0,
                0.72,
                4.5,
                0,
                0,
                26000.0,
                visibleBundleThreePlusRate,
                lastDropGoodZoneRate,
                expectedEmptyKm,
                12.0,
                deliveryCorridorQuality,
                0.10,
                84.0,
                0.0,
                16.0,
                70.0,
                4.0,
                6.0,
                12.0
        );
    }
}
