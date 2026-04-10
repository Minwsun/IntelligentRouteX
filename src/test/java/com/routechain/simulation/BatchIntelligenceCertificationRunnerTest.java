package com.routechain.simulation;

import com.routechain.infra.DispatchFactSink;
import com.routechain.infra.JsonlDispatchFactSink;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchIntelligenceCertificationRunnerTest {

    @Test
    void shouldWriteBatchIntelligenceSummaryFromCandidateFacts() throws IOException {
        Path buildRoot = Path.of("build", "routechain-apex");
        deleteRecursively(buildRoot);
        JsonlDispatchFactSink sink = new JsonlDispatchFactSink(buildRoot.resolve("facts"));

        sink.recordCandidate(candidate("trace-selected", true, "EXTENSION_LOCAL", 2, 0.92, 0.78, 0.72, 1.1));
        sink.recordCandidate(candidate("trace-single", false, "SINGLE_LOCAL", 1, 0.71, 0.61, 0.55, 1.5));
        sink.recordCandidate(candidate("trace-ext-alt", false, "EXTENSION_LOCAL", 2, 0.80, 0.69, 0.63, 1.3));
        sink.recordDecision(new DispatchFactSink.DecisionFact(
                "trace-selected",
                "omega-proof-run",
                42L,
                "driver-1",
                "policy-A",
                "MAINLINE_REALISTIC",
                "FULL",
                0.92,
                0.8,
                2,
                Map.of(),
                Map.of(),
                new double[0],
                new double[0],
                "batch selected",
                "SHADOW_FAST",
                0,
                "skip",
                "none",
                "offline",
                "instant",
                "SIMULATED_ASYNC",
                5L,
                "EXTENSION_LOCAL",
                0,
                1.1,
                null,
                Instant.parse("2026-04-10T00:00:00Z")
        ));

        assertDoesNotThrow(() -> BatchIntelligenceCertificationRunner.main(new String[]{"certification"}));

        Path json = buildRoot.resolve("benchmarks").resolve("certification").resolve("batch-intelligence-certification-certification.json");
        assertTrue(Files.exists(json));
        String payload = Files.readString(json);
        assertTrue(payload.contains("\"overallPass\": true"));
        assertTrue(payload.contains("\"batch2SampleCount\": 1"));
    }

    private DispatchFactSink.CandidateFact candidate(String traceId,
                                                     boolean selected,
                                                     String selectionBucket,
                                                     int bundleSize,
                                                     double utility,
                                                     double landing,
                                                     double postDrop,
                                                     double emptyKm) {
        return new DispatchFactSink.CandidateFact(
                traceId,
                "omega-proof-run",
                42L,
                "driver-1",
                "bundle-1",
                selected,
                "policy-A",
                "MAINLINE_REALISTIC",
                "FULL",
                selectionBucket,
                bundleSize,
                utility,
                0.8,
                0.71,
                0.59,
                0.62,
                0.15,
                0.48,
                0.55,
                0.51,
                "instant",
                "SIMULATED_ASYNC",
                0.72,
                0.81,
                0.64,
                0.47,
                0.29,
                0.18,
                "osm-osrm-surrogate-v1",
                Map.<String, Object>of(
                        "lastDropLandingScore", landing,
                        "postDropDemandProbability", postDrop,
                        "expectedPostCompletionEmptyKm", emptyKm,
                        "marginalDeadheadPerAddedOrder", 1.1
                ),
                Map.of(),
                new double[] {0.1, 0.2},
                new double[] {0.3, 0.4},
                Map.of("plan-ranker-model", "dispatch-ranker-lambdamart-v1"),
                Instant.parse("2026-04-10T00:00:00Z")
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
