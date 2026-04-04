package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads benchmark certification baselines committed in the repository.
 */
public final class BenchmarkCertificationConfigLoader {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("benchmark-baselines");
    private static final Path BASELINE_JSON = ROOT.resolve("route-ai-certification-baseline.json");
    private static final Path SCENARIO_MATRIX_JSON = ROOT.resolve("certification-scenarios.json");

    private BenchmarkCertificationConfigLoader() {}

    public static BenchmarkCertificationBaseline loadBaseline() {
        return readJson(BASELINE_JSON, BenchmarkCertificationBaseline.class, "route AI certification baseline");
    }

    public static BenchmarkCertificationScenarioMatrix loadScenarioMatrix() {
        return readJson(
                SCENARIO_MATRIX_JSON,
                BenchmarkCertificationScenarioMatrix.class,
                "benchmark certification scenario matrix");
    }

    private static <T> T readJson(Path path, Class<T> type, String label) {
        try {
            if (Files.notExists(path)) {
                throw new IllegalStateException("Missing " + label + " at " + path);
            }
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + label, e);
        }
    }
}
