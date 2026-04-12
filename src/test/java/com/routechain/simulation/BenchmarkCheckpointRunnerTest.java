package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCheckpointRunnerTest {

    @Test
    void shouldWriteDirtyTriageCheckpointPack() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        BenchmarkArtifactWriter.writeBenchmarkAuthoritySnapshot(new BenchmarkAuthoritySnapshot(
                BenchmarkSchema.VERSION,
                "smoke",
                Instant.parse("2026-04-12T13:00:00Z"),
                "abc123",
                true,
                true,
                false,
                List.of("build.gradle.kts"),
                List.of("build.gradle.kts"),
                List.of("benchmark authority paths are dirty")
        ));
        BenchmarkArtifactWriter.writeRouteAiCertificationSummary(new RouteAiCertificationSummary(
                BenchmarkSchema.VERSION,
                "route-ai-certification-smoke",
                "local-production-small-smoke",
                "CLEAR",
                "Legacy",
                "Omega-current",
                true,
                true,
                8.0,
                12.0,
                120.0,
                180.0,
                true,
                true,
                0.4,
                0.2,
                -1.5,
                0.8,
                true,
                true,
                true,
                true,
                true,
                "graphAffinityScoring",
                List.of("all green")
        ));
        BenchmarkArtifactWriter.writeRepoIntelligenceCertificationSummary(new RepoIntelligenceCertificationSummary(
                BenchmarkSchema.VERSION,
                "repo-intelligence-smoke",
                Instant.parse("2026-04-12T13:05:00Z"),
                "abc123",
                "21.0.2",
                "local-production-small-50",
                List.of(42L),
                List.of("CLEAR"),
                new CertificationGateResult("Correctness", true, List.of()),
                new CertificationGateResult("Latency", true, List.of()),
                new CertificationGateResult("Route Quality", false, List.of("heavy rain still failing")),
                new CertificationGateResult("Continuity", true, List.of()),
                new CertificationGateResult("Stress/Safety", true, List.of()),
                new CertificationGateResult("Auxiliary", true, List.of()),
                new LegacyReferenceResult(false, 0, 0.5, 0.1, -0.3, List.of()),
                List.of(),
                new RouteQualityBlockerSummary(
                        BenchmarkSchema.VERSION,
                        "route-quality-blockers-smoke",
                        Instant.parse("2026-04-12T13:05:00Z"),
                        "abc123",
                        List.of(),
                        List.of("triage-only")
                ),
                false,
                "FAIL",
                List.of("route quality still red")
        ));

        BenchmarkCheckpointRunner.main(new String[]{"smoke"});

        Path json = root.resolve("certification").resolve("benchmark-checkpoint-smoke.json");
        Path markdown = root.resolve("certification").resolve("benchmark-checkpoint-smoke.md");
        assertTrue(Files.exists(json));
        assertTrue(Files.exists(markdown));
        String jsonContent = Files.readString(json);
        String markdownContent = Files.readString(markdown);
        assertTrue(jsonContent.contains("\"checkpointStatus\": \"DIRTY_TRIAGE_ONLY\""));
        assertTrue(markdownContent.contains("Checkpoint status: DIRTY_TRIAGE_ONLY"));
        assertTrue(Files.readString(root.resolve("benchmark_checkpoint.csv")).contains("DIRTY_TRIAGE_ONLY"));
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
