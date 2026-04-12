package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteIntelligenceVerdictRunnerTest {

    @Test
    void shouldWriteSmokeVerdictFromCertificationAndAblationArtifacts() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

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
                -1.1,
                -0.2,
                1.9,
                -1.4,
                false,
                false,
                false,
                true,
                true,
                "graphAffinityScoring",
                List.of("legacy warning only")
        ));
        BenchmarkArtifactWriter.writeRepoIntelligenceCertificationSummary(new RepoIntelligenceCertificationSummary(
                BenchmarkSchema.VERSION,
                "repo-intelligence-smoke",
                Instant.parse("2026-04-04T00:00:00Z"),
                "abc123",
                "21.0.2",
                "local-production-small-50",
                List.of(42L),
                List.of("CLEAR"),
                new CertificationGateResult("Correctness", true, List.of()),
                new CertificationGateResult("Latency", true, List.of()),
                new CertificationGateResult("Route Quality", true, List.of()),
                new CertificationGateResult("Continuity", true, List.of()),
                new CertificationGateResult("Stress/Safety", true, List.of()),
                new CertificationGateResult("Auxiliary", true, List.of()),
                new LegacyReferenceResult(true, 2, -1.3, -0.4, 2.0, List.of("legacy ref still stronger on smoke")),
                List.of(new ScenarioGroupCertificationResult(
                        "CLEAR",
                        1,
                        List.of(42L),
                        8.0,
                        12.0,
                        22.0,
                        88.0,
                        60.0,
                        0.0,
                        99.0,
                        31.0,
                        1.4,
                        97.0,
                        0.75,
                        0.6,
                        0.18,
                        0.95,
                        100.0,
                        20.0,
                        0.0,
                        0.0,
                        12.0,
                        1.4,
                        true,
                        true,
                        true,
                        List.of()
                )),
                new RouteQualityBlockerSummary(
                        BenchmarkSchema.VERSION,
                        "route-quality-blockers-smoke",
                        Instant.parse("2026-04-04T00:00:00Z"),
                        "abc123",
                        List.of(new RouteQualityBlockerBucketSummary(
                                "HEAVY_RAIN",
                                "blocker",
                                2,
                                -5.1,
                                -4.0,
                                -2.6,
                                0.9,
                                0.4,
                                6.0,
                                0.28,
                                -8.4,
                                -4.5,
                                0.31,
                                1.9,
                                3.3,
                                1.1,
                                1.5,
                                List.of("eta_bias", "deadhead_inflation", "post_drop_blind_zone"),
                                List.of("heavy rain remains unsolved")
                        )),
                        List.of("phase-3 blocker summary")
                ),
                true,
                "PASS_WITH_WARNING",
                List.of("smoke lane green")
        ));

        writeAblation("NO_NEURAL_PRIOR", 1.2, 0.8, -1.0, 0.03, 0.01, 0.7);
        writeAblation("NO_CONTINUATION", 1.1, 0.6, -0.8, 0.04, 0.02, 0.9);
        writeAblation("NO_BATCH_VALUE", 0.7, 0.5, -0.2, 0.02, 0.03, 0.2);
        writeAblation("NO_STRESS_AI_GATE", 0.9, 0.7, -0.7, 0.03, 0.04, 0.8);
        writeAblation("NO_POSITIONING_MODEL", 0.8, 0.4, -0.5, 0.02, 0.03, 0.6);

        assertDoesNotThrow(() -> RouteIntelligenceVerdictRunner.main(new String[]{"smoke"}));

        Path json = root.resolve("certification").resolve("route-intelligence-verdict-smoke.json");
        Path markdown = root.resolve("certification").resolve("route-intelligence-verdict-smoke.md");
        assertTrue(Files.exists(json));
        assertTrue(Files.exists(markdown));
        String jsonContent = Files.readString(json);
        String markdownContent = Files.readString(markdown);
        assertTrue(jsonContent.contains("\"aiVerdict\": \"YES\""));
        assertTrue(jsonContent.contains("\"routingVerdict\": \"PARTIAL\""));
        assertTrue(markdownContent.contains("Claim Readiness: INTERNAL_ONLY"));
    }

    private void writeAblation(String mode,
                               double gain,
                               double completion,
                               double deadhead,
                               double routingScore,
                               double networkScore,
                               double postDrop) {
        BenchmarkArtifactWriter.writeAblationResult(new PolicyAblationResult(
                BenchmarkSchema.VERSION,
                "ai-influence-smoke-" + mode.toLowerCase(),
                "ai-influence/smoke/" + mode.toLowerCase(),
                mode,
                "OMEGA_FULL",
                gain > 0.5 ? "FULL_BETTER" : "MIXED",
                gain,
                BenchmarkStatistics.summarize("overallGainPercent", mode, List.of(gain, gain + 0.1)),
                BenchmarkStatistics.summarize("completionRateDelta", mode, List.of(completion, completion + 0.1)),
                BenchmarkStatistics.summarize("deadheadDistanceRatioDelta", mode, List.of(deadhead, deadhead - 0.1)),
                List.of(
                        BenchmarkStatistics.summarize("routingScoreDelta", mode, List.of(routingScore, routingScore + 0.01)),
                        BenchmarkStatistics.summarize("networkScoreDelta", mode, List.of(networkScore, networkScore + 0.01)),
                        BenchmarkStatistics.summarize("postDropOrderHitRateDelta", mode, List.of(postDrop, postDrop + 0.2))
                )
        ));
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
