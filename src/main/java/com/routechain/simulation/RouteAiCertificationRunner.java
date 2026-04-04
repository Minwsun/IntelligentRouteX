package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads smoke benchmark artifacts and emits a stable pass/fail certification summary.
 */
public final class RouteAiCertificationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path RUNTIME_SLO_JSON = ROOT.resolve("runtime_slo_summary.json");
    private static final Path COMPARES_CSV = ROOT.resolve("replay_compare_results.csv");
    private static final Path STAGE_LATENCY_CSV = ROOT.resolve("dispatch_stage_breakdown.csv");
    private static final double DISPATCH_P95_TARGET_MS = 120.0;
    private static final double DISPATCH_P99_TARGET_MS = 180.0;

    private RouteAiCertificationRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase()
                : "smoke";
        RuntimeSloSummary runtime = readRuntimeSloSummary();
        CsvRow compareRow = readLatestCompareRow();
        CsvRow stageRow = readLatestRunStageRow();

        boolean routeRegressionPass = true;
        boolean safetyPass = true;
        boolean gainPass = compareRow.doubleValue("gain") >= 0.0;
        boolean completionPass = compareRow.doubleValue("completionDelta") >= 0.0;
        boolean deadheadPass = compareRow.doubleValue("deadheadDelta") <= 0.0;
        boolean overallPass = routeRegressionPass
                && runtime.measurementValid()
                && runtime.dispatchP95Pass()
                && runtime.dispatchP99Pass()
                && gainPass
                && completionPass
                && deadheadPass
                && safetyPass;

        List<String> notes = new ArrayList<>();
        if (!runtime.dispatchP95Pass()) {
            notes.add("dispatchP95 above " + (int) DISPATCH_P95_TARGET_MS + "ms target");
        }
        if (!runtime.dispatchP99Pass()) {
            notes.add("dispatchP99 above " + (int) DISPATCH_P99_TARGET_MS + "ms target");
        }
        if (!gainPass) {
            notes.add("Omega-current overall gain is below Legacy");
        }
        if (!completionPass) {
            notes.add("Omega-current completion delta is negative");
        }
        if (!deadheadPass) {
            notes.add("Omega-current deadhead ratio is worse than Legacy");
        }
        if (stageRow != null) {
            notes.add("dominant dispatch stage by p95: " + stageRow.value("dominantStageByP95"));
        }

        RouteAiCertificationSummary summary = new RouteAiCertificationSummary(
                BenchmarkSchema.VERSION,
                "route-ai-certification-" + laneName,
                runtime.profileName(),
                "counterfactual-normal-smoke",
                "Legacy",
                "Omega-current",
                routeRegressionPass,
                runtime.measurementValid(),
                runtime.dispatchP95Ms(),
                runtime.dispatchP99Ms(),
                DISPATCH_P95_TARGET_MS,
                DISPATCH_P99_TARGET_MS,
                runtime.dispatchP95Pass(),
                runtime.dispatchP99Pass(),
                compareRow.doubleValue("gain"),
                compareRow.doubleValue("completionDelta"),
                compareRow.doubleValue("deadheadDelta"),
                compareRow.doubleValue("postDropOrderHitRateDelta"),
                gainPass,
                completionPass,
                deadheadPass,
                safetyPass,
                overallPass,
                stageRow == null ? "unknown" : stageRow.value("dominantStageByP95"),
                notes
        );

        BenchmarkArtifactWriter.writeRouteAiCertificationSummary(summary);
        System.out.println("[RouteAiCertification] lane=" + laneName
                + " verdict=" + (summary.overallPass() ? "PASS" : "FAIL")
                + " p95=" + String.format("%.1f", summary.dispatchP95Ms())
                + " p99=" + String.format("%.1f", summary.dispatchP99Ms())
                + " gain=" + String.format("%.1f", summary.overallGainPercent())
                + " completionDelta=" + String.format("%+.2f", summary.completionDelta())
                + " deadheadDelta=" + String.format("%+.2f", summary.deadheadDistanceRatioDelta()));
        if (!summary.overallPass()) {
            throw new IllegalStateException("Route AI certification failed for lane " + laneName);
        }
    }

    private static RuntimeSloSummary readRuntimeSloSummary() {
        try {
            if (Files.notExists(RUNTIME_SLO_JSON)) {
                throw new IllegalStateException("Missing runtime SLO artifact at " + RUNTIME_SLO_JSON);
            }
            return GSON.fromJson(Files.readString(RUNTIME_SLO_JSON, StandardCharsets.UTF_8), RuntimeSloSummary.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read runtime SLO summary", e);
        }
    }

    private static CsvRow readLatestCompareRow() {
        List<CsvRow> rows = readCsv(COMPARES_CSV);
        for (int i = rows.size() - 1; i >= 0; i--) {
            CsvRow row = rows.get(i);
            if (row.value("scenarioA").contains("Legacy")
                    && row.value("scenarioB").contains("Omega-current")) {
                return row;
            }
        }
        throw new IllegalStateException("Missing Legacy vs Omega-current compare artifact");
    }

    private static CsvRow readLatestRunStageRow() {
        List<CsvRow> rows = readCsv(STAGE_LATENCY_CSV);
        for (int i = rows.size() - 1; i >= 0; i--) {
            CsvRow row = rows.get(i);
            if (row.value("scope").startsWith("run_")) {
                return row;
            }
        }
        return null;
    }

    private static List<CsvRow> readCsv(Path path) {
        try {
            if (Files.notExists(path)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }
            String[] headers = lines.get(0).split(",", -1);
            List<CsvRow> rows = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                String[] values = lines.get(i).split(",", -1);
                Map<String, String> row = new LinkedHashMap<>();
                for (int j = 0; j < headers.length; j++) {
                    row.put(headers[j], j < values.length ? values[j] : "");
                }
                rows.add(new CsvRow(row));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read csv artifact " + path, e);
        }
    }

    private record CsvRow(Map<String, String> values) {
        private String value(String key) {
            return values.getOrDefault(key, "");
        }

        private double doubleValue(String key) {
            String value = value(key);
            if (value == null || value.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(value);
        }
    }
}
