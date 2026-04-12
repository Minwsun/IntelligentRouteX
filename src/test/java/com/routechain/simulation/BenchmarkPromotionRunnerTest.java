package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkPromotionRunnerTest {
    private static final Gson GSON = GsonSupport.pretty();

    @Test
    void shouldRejectDegradedCheckpointBaselinePromotion() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        BenchmarkArtifactWriter.writeBenchmarkCheckpointSummary(new BenchmarkCheckpointSummary(
                BenchmarkSchema.VERSION,
                "certification",
                Instant.parse("2026-04-12T16:00:00Z"),
                "abc123",
                "certification-abc123-1000",
                "CLEAN_CANONICAL_CHECKPOINT",
                true,
                false,
                true,
                false,
                true,
                true,
                true,
                true,
                "PASS",
                "FAIL",
                "PARTIAL",
                List.of("route-ai summary fell back to smoke for non-smoke lane"),
                List.of("checkpoint degraded")
        ));

        BenchmarkPromotionRunner.main(new String[]{"checkpoint", "certification"});

        String promotionsCsv = Files.readString(root.resolve("benchmark_promotions.csv"));
        assertTrue(promotionsCsv.contains("REJECTED_BASELINE"));
        assertTrue(Files.notExists(root.resolve("governance").resolve("benchmark-baseline-current.json")));
    }

    @Test
    void shouldPromoteCleanCheckpointToBaselineRegistry() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        BenchmarkArtifactWriter.writeBenchmarkCheckpointSummary(new BenchmarkCheckpointSummary(
                BenchmarkSchema.VERSION,
                "certification",
                Instant.parse("2026-04-12T16:30:00Z"),
                "def456",
                "certification-def456-2000",
                "CLEAN_CANONICAL_CHECKPOINT",
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                "PASS",
                "PASS",
                "PARTIAL",
                List.of(),
                List.of("clean canonical checkpoint")
        ));

        BenchmarkPromotionRunner.main(new String[]{"checkpoint", "certification"});

        Path currentBaseline = root.resolve("governance").resolve("benchmark-baseline-current.json");
        assertTrue(Files.exists(currentBaseline));
        BenchmarkBaselineRef baseline = GSON.fromJson(Files.readString(currentBaseline), BenchmarkBaselineRef.class);
        assertEquals("certification-def456-2000", baseline.checkpointId());
        assertEquals("certification", baseline.laneName());
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
