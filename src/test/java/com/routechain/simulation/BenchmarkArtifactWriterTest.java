package com.routechain.simulation;

import com.routechain.domain.Enums.DeliveryServiceTier;
import com.routechain.graph.FutureCellValue;
import com.routechain.graph.GraphAffinitySnapshot;
import com.routechain.graph.GraphExplanationTrace;
import com.routechain.graph.GraphNodeRef;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        assertTrue(runCsv.contains("postDropOrderHitRate"));
        assertTrue(runCsv.contains("dispatchP95Ms"));
        assertTrue(runCsv.contains("businessScore"));
        assertTrue(runCsv.contains("dominantServiceTier"));
        assertTrue(runCsv.contains("merchantPrepMaeMinutes"));
        assertTrue(Files.exists(root.resolve("latency_breakdown.csv")));
        assertTrue(Files.exists(root.resolve("dispatch_stage_breakdown.csv")));
        assertTrue(Files.exists(root.resolve("scenario_acceptance.csv")));
        assertTrue(compareCsv.contains("candidate-run"));
        assertTrue(compareCsv.contains("dispatchP95MsDelta"));
        assertTrue(runJson.contains("\"prePickupAugmentRate\""));
        assertTrue(runJson.contains("\"holdOnlySelectionRate\""));
        assertTrue(runJson.contains("\"postDropOrderHitRate\""));
        assertTrue(runJson.contains("\"latency\""));
        assertTrue(runJson.contains("\"intelligence\""));
        assertTrue(runJson.contains("\"acceptance\""));
        assertTrue(runJson.contains("\"serviceTierBreakdown\""));
        assertTrue(compareJson.contains("\"realAssignmentRateDelta\""));
        assertTrue(compareJson.contains("\"waveAssemblyWaitRateDelta\""));
        assertTrue(compareJson.contains("\"thirdOrderLaunchRateDelta\""));
        assertTrue(compareJson.contains("\"postDropOrderHitRateDelta\""));
        assertTrue(compareJson.contains("\"serviceTierBreakdownDelta\""));
    }

    @Test
    void shouldPersistManifestStatsAndAblationArtifacts() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        BenchmarkRunManifest manifest = new BenchmarkRunManifest(
                BenchmarkSchema.VERSION,
                "manifest-test-1",
                "hybridBenchmark",
                Instant.parse("2026-03-31T00:00:00Z"),
                "abc123",
                List.of(42L, 77L, 123L),
                List.of(10, 25, 50),
                BenchmarkEnvironmentProfile.detect("local-production-small-50", 50, "SIMULATED_ASYNC"),
                1,
                3,
                180,
                200_000L,
                "multi-seed,ci95",
                "test-manifest",
                List.of(new BenchmarkCaseSpec(
                        "case-1", "production-realism", "normal",
                        DeliveryServiceTier.classifyScenario("normal").wireValue(),
                        "local-production-small-25", "SIMULATED_ASYNC",
                        "simulation", "trackA", 1200, 25, 1.0, 0.4,
                        "CLEAR", 42L, 0))
                ,
                List.of(new PolicyCandidateRecord(
                        "Omega-current",
                        SolverType.TIMEFOLD_ONLINE,
                        "execution-first-hybrid",
                        "execution-first-default",
                        List.of("normal")
                )),
                List.of(new CounterfactualRunSpec(
                        "normal",
                        42L,
                        25,
                        List.of("Legacy", "Omega-current"),
                        120_000L
                ))
        );
        BenchmarkStatSummary stat = BenchmarkStatistics.summarize(
                "overallGainPercent",
                "trackA/normal",
                List.of(1.2, 2.4, 1.8)
        );
        PolicyAblationResult ablation = new PolicyAblationResult(
                BenchmarkSchema.VERSION,
                "ablation-1",
                "trackA/normal",
                "Legacy",
                "Omega-current",
                "CANDIDATE_BETTER",
                1.8,
                stat,
                BenchmarkStatistics.summarizeComparison(
                        "completionRate",
                        "trackA/normal",
                        List.of(71.0, 72.0, 73.0),
                        List.of(75.0, 76.0, 75.5)),
                BenchmarkStatistics.summarizeComparison(
                        "deadheadDistanceRatio",
                        "trackA/normal",
                        List.of(31.0, 32.0, 30.5),
                        List.of(24.0, 23.8, 24.4)),
                List.of()
        );

        BenchmarkArtifactWriter.writeManifest(manifest);
        BenchmarkArtifactWriter.writeStatSummary(stat);
        BenchmarkArtifactWriter.writeAblationResult(ablation);

        assertTrue(Files.exists(root.resolve("manifests").resolve("manifest-test-1.json")));
        assertTrue(Files.exists(root.resolve("stats").resolve("trackA_normal-overallGainPercent.json")));
        assertTrue(Files.exists(root.resolve("ablations").resolve("ablation-1.json")));

        String manifestsCsv = Files.readString(root.resolve("benchmark_manifests.csv"));
        String statsCsv = Files.readString(root.resolve("benchmark_stats.csv"));
        String ablationsCsv = Files.readString(root.resolve("policy_ablations.csv"));
        String manifestJson = Files.readString(root.resolve("manifests").resolve("manifest-test-1.json"));

        assertTrue(manifestsCsv.contains("manifest-test-1"));
        assertTrue(manifestsCsv.contains("environmentProfile"));
        assertTrue(manifestsCsv.contains("policyCandidateCount"));
        assertTrue(statsCsv.contains("metricClass"));
        assertTrue(statsCsv.contains("overallGainPercent"));
        assertTrue(ablationsCsv.contains("ablation-1"));
        assertTrue(manifestJson.contains("\"schemaVersion\": \"v2\""));
    }

    @Test
    void shouldPersistDriftSnapshotArtifact() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        DispatchDriftSnapshot snapshot = new DispatchDriftSnapshot(
                BenchmarkSchema.VERSION,
                "counterfactual/Omega-current",
                "deadheadDistanceRatio",
                30.0,
                24.0,
                -6.0,
                3.0,
                true
        );
        BenchmarkArtifactWriter.writeDriftSnapshot(snapshot);

        assertTrue(Files.exists(root.resolve("drift")
                .resolve("counterfactual_Omega-current-deadheadDistanceRatio.json")));
        String driftCsv = Files.readString(root.resolve("drift_snapshots.csv"));
        assertTrue(driftCsv.contains("deadheadDistanceRatio"));
        assertTrue(driftCsv.contains("counterfactual_Omega-current"));
    }

    @Test
    void shouldPersistRouteAiCertificationArtifacts() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        RouteAiCertificationSummary summary = new RouteAiCertificationSummary(
                BenchmarkSchema.VERSION,
                "route-ai-certification-smoke",
                "local-production-small-smoke",
                "counterfactual-normal-smoke",
                "Legacy",
                "Omega-current",
                true,
                true,
                118.0,
                176.0,
                120.0,
                180.0,
                true,
                true,
                1.4,
                0.8,
                -2.2,
                12.5,
                true,
                true,
                true,
                true,
                true,
                "graphAffinityScoring",
                List.of("all gates green")
        );

        BenchmarkArtifactWriter.writeRouteAiCertificationSummary(summary);

        assertTrue(Files.exists(root.resolve("certification").resolve("route-ai-certification-smoke.json")));
        assertTrue(Files.exists(root.resolve("certification").resolve("route-ai-certification-smoke.md")));
        String certificationCsv = Files.readString(root.resolve("route_ai_certification.csv"));
        assertTrue(certificationCsv.contains("route-ai-certification-smoke"));
        assertTrue(certificationCsv.contains("Omega-current"));
    }

    @Test
    void shouldPersistRepoIntelligenceCertificationArtifacts() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        RepoIntelligenceCertificationSummary summary = new RepoIntelligenceCertificationSummary(
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
                new LegacyReferenceResult(false, 0, 1.2, 0.4, -2.0, List.of("legacy reference healthy")),
                List.of(new ScenarioGroupCertificationResult(
                        "CLEAR",
                        1,
                        List.of(42L),
                        8.0,
                        12.0,
                        20.0,
                        88.0,
                        60.0,
                        0.0,
                        99.0,
                        32.0,
                        1.4,
                        98.0,
                        0.76,
                        0.8,
                        0.14,
                        0.92,
                        100.0,
                        20.0,
                        0.0,
                        0.0,
                        12.0,
                        1.5,
                        true,
                        true,
                        true,
                        List.of()
                )),
                true,
                "PASS",
                List.of("smoke lane green")
        );

        BenchmarkArtifactWriter.writeRepoIntelligenceCertificationSummary(summary);

        assertTrue(Files.exists(root.resolve("certification").resolve("repo-intelligence-smoke.json")));
        assertTrue(Files.exists(root.resolve("certification").resolve("repo-intelligence-smoke.md")));
        String csv = Files.readString(root.resolve("repo_intelligence_certification.csv"));
        assertTrue(csv.contains("repo-intelligence-smoke"));
        assertTrue(csv.contains("legacyUnderperforming"));
    }

    @Test
    void shouldPersistControlRoomArtifacts() throws IOException {
        Path root = Path.of("build", "routechain-apex", "benchmarks");
        deleteRecursively(root);

        RunReport report = createReport("control-room-run", "instant-normal", 4.2, 31.0, 58.0, 1.1);
        ControlRoomFrame frame = new ControlRoomFrame(
                report.runId(),
                report.scenarioName(),
                Instant.parse("2026-04-02T00:00:00Z"),
                report.dominantServiceTier(),
                "MAINLINE_REALISTIC",
                "NORMAL",
                "SIMULATED_ASYNC",
                "instant-normal | tier=instant | dh/completed=1.82km",
                List.of("Top future cell GRID-2-4 has postDrop=0.78"),
                report.latency(),
                report.intelligence(),
                report.acceptance(),
                new ForecastDriftSnapshot(report.runId(), report.scenarioName(), 0.08, 3.1, 0.18, 0.86, 0.10, false, "HEALTHY", "forecast healthy"),
                new RoutePolicyProfile(
                        "NORMAL",
                        "instant",
                        "MAINLINE_REALISTIC",
                        "SIMULATED_ASYNC",
                        "instant-local-first-execution-gate",
                        "balanced-reserve-shaping",
                        "epsilon-greedy",
                        0.10,
                        0.24,
                        3.2,
                        0.40,
                        0.35,
                        "CLEAR",
                        Map.of("NORMAL", 0.81, "SHORTAGE", 0.69),
                        Map.of("NORMAL", 12, "SHORTAGE", 5)
                ),
                List.of(new CellValueSnapshot(
                        "8a1f94d6b59ffff",
                        "H3-r8",
                        "instant",
                        10.772,
                        106.701,
                        1.2,
                        1.5,
                        1.9,
                        2.1,
                        2.4,
                        0.42,
                        0.31,
                        0.18,
                        0.78,
                        0.22,
                        0.61,
                        0.34,
                        0.82
                )),
                List.of(new FutureCellValue(
                        "8a1f94d6b59ffff",
                        "instant",
                        10,
                        1.9,
                        0.78,
                        0.22,
                        0.73,
                        0.84,
                        "futureCell=0.84 graphCentrality=0.73 demand10=1.90 postDrop=0.78"
                )),
                List.of(new DriverFutureValue(
                        "DRV-1",
                        "8a1f94d6b59fff0",
                        "8a1f94d6b59ffff",
                        "instant",
                        10,
                        0.42,
                        0.82,
                        0.78,
                        0.22,
                        0.61,
                        0.79,
                        "Shift DRV-1 to 8a1f94d6b59ffff because futureValue=0.79"
                )),
                List.of(new GraphAffinitySnapshot(
                        "DRIVER_IN_ZONE",
                        new GraphNodeRef("DRIVER", "driver-DRV-1", "DRV-1", "8a1f94d6b59fff0", 10.771, 106.700),
                        new GraphNodeRef("ZONE", "zone-8a1f94d6b59ffff", "Zone 8a1f94d6b59ffff", "8a1f94d6b59ffff", 10.772, 106.701),
                        0.81,
                        "driver-zone affinity=0.81 postDrop=0.78 emptyRisk=0.22 demand10=1.90"
                )),
                List.of(new GraphExplanationTrace(
                        "graph-control-room-run-DRV-1-bundle-1",
                        "DRV-1",
                        "ORD-1",
                        "8a1f94d6b59ffaa",
                        "8a1f94d6b59ffff",
                        0.77,
                        0.81,
                        0.70,
                        0.82,
                        0.76,
                        "graphAffinity=0.77 topology=0.81 bundleCompat=0.70 futureCell=0.82 congestionSafe=0.76 endCell=8a1f94d6b59ffff"
                )),
                List.of(new MarketplaceEdge(
                        "edge-DRV-1-ORD-1",
                        "DRV-1",
                        "ORD-1",
                        "instant",
                        "8a1f94d6b59ffaa",
                        "8a1f94d6b59ffff",
                        3.6,
                        1.1,
                        0.84,
                        0.72,
                        0.77,
                        0.81,
                        false,
                        "local edge with dh=1.10km eta=3.6m postDrop=0.78 emptyRisk=0.22 graph=0.77",
                        new GraphExplanationTrace(
                                "graph-control-room-run-DRV-1-bundle-1",
                                "DRV-1",
                                "ORD-1",
                                "8a1f94d6b59ffaa",
                                "8a1f94d6b59ffff",
                                0.77,
                                0.81,
                                0.70,
                                0.82,
                                0.76,
                                "graphAffinity=0.77 topology=0.81 bundleCompat=0.70 futureCell=0.82 congestionSafe=0.76 endCell=8a1f94d6b59ffff"
                        )
                )),
                List.of(new RiderCopilotRecommendation(
                        "copilot-1-8a1f94d6b59ffff",
                        "idle-driver",
                        "instant",
                        "SHIFT_700M_TO_CELL",
                        "8a1f94d6b59ffff",
                        10.772,
                        106.701,
                        0.82,
                        0.78,
                        0.22,
                        0.61,
                        "SHIFT_700M_TO_CELL -> postDrop=0.78 emptyRisk=0.22"
                )),
                List.of(new ModelPromotionDecision(
                        "neural-route-prior-model",
                        "routefinder-v1",
                        "rrnco-v1",
                        "READY_FOR_COUNTERFACTUAL",
                        "challenger lane may be evaluated on the same event tape",
                        false,
                        30000,
                        95.0,
                        0.72,
                        0.08
                )),
                null
        );

        BenchmarkArtifactWriter.writeControlRoomFrame(frame);

        assertTrue(Files.exists(root.resolve("control-room").resolve("frames").resolve("control-room-run.json")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("control_room_latest.json")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("control_room_latest.md")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("city_twin_cells.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("future_cell_values.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("driver_future_values.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("graph_affinities.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("graph_explanations.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("marketplace_edges.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("rider_copilot.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("model_promotion.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("policy_profile.csv")));
        assertTrue(Files.exists(root.resolve("control-room").resolve("forecast_drift.csv")));

        String markdown = Files.readString(root.resolve("control-room").resolve("control_room_latest.md"));
        assertTrue(markdown.contains("RouteChain Control Room"));
        assertTrue(markdown.contains("Driver Future Value"));
        assertTrue(markdown.contains("Future Cell Values"));
        assertTrue(markdown.contains("Marketplace Edges"));
        assertTrue(markdown.contains("Graph Affinities"));
        assertTrue(markdown.contains("Graph Explanations"));
        assertTrue(markdown.contains("Rider Copilot"));
        assertTrue(markdown.contains("Model Ops"));
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
                12.0,
                1.18,
                1.82,
                1.31,
                42.0,
                1.05,
                1.14,
                0.99,
                DispatchStageBreakdown.empty(),
                new LatencyBreakdown(22.0, 18.0, 95.0, 118.0, 6.0, 10.0, 34.0, 58.0, 1200.0, 2100.0, 8.5, 12, 10),
                new IntelligenceScorecard(0.72, 0.68, 0.64, 0.61, 0.66, 0.63, 0.58, 1.0, 0.74, 0.69, 0.54, 0.21, 0.82, 0.13, "STRONG", "PASSING"),
                new ScenarioAcceptanceResult(scenario, "instant", "local-production-small-50", true, true, true, true, true, "STRONG", "PASSING", ""),
                "instant",
                Map.of("instant", new ServiceTierMetrics("instant", 100, 84, 84.0, 45.0, 28000.0)),
                new ForecastCalibrationSummary(4.5, 3.1, -0.05, 0.42),
                DispatchRecoveryDecomposition.empty()
        );
    }
}
