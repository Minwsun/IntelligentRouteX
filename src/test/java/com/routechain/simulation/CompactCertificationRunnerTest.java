package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactCertificationRunnerTest {

    @Test
    void shouldReturnCompactReadyWhenCompactStaysWithinOmegaGate() {
        CompactBenchmarkSummary summary = summaryWithCase(
                benchmarkCase("clear-normal", 97.0, 96.9, 95.0, 95.0, 1.10, 1.05, 1.80, 1.75, 42.0, 41.0));

        CompactCertificationSummary certification = CompactCertificationRunner.evaluate(summary);

        assertEquals("COMPACT_READY", certification.verdict());
        assertEquals(true, certification.overallPass());
    }

    @Test
    void shouldReturnCompactNotReadyWhenCompactFallsBehindOmegaOnCoreKpis() {
        CompactBenchmarkSummary summary = summaryWithCase(
                benchmarkCase("clear-normal", 94.0, 96.5, 91.0, 95.0, 1.45, 1.10, 2.40, 1.70, 36.0, 42.0));

        CompactCertificationSummary certification = CompactCertificationRunner.evaluate(summary);

        assertEquals("COMPACT_NOT_READY", certification.verdict());
        assertEquals(false, certification.overallPass());
    }

    private CompactBenchmarkSummary summaryWithCase(CompactBenchmarkCase benchmarkCase) {
        return new CompactBenchmarkSummary(
                Instant.parse("2026-04-12T09:00:00Z"),
                "compactCertification",
                "HCMC + INSTANT",
                "NearestGreedyBaseline",
                List.of("clear-normal"),
                Map.of(
                        "AFTER_ACCEPT", "assignment activated on runtime driver sequence; compact v1 accept-equivalent, not marketplace offer acceptance",
                        "AFTER_TERMINAL", "all orders in the decision are delivered or cancelled",
                        "AFTER_POST_DROP_WINDOW", "post-drop hit observed or post-drop window expired; canonical KPI and weight-update stage"),
                "AFTER_POST_DROP_WINDOW",
                "AFTER_POST_DROP_WINDOW",
                "weighted_by_support",
                List.of(benchmarkCase),
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                Map.of(),
                Map.of(),
                Map.of(),
                0,
                0,
                0,
                0.0,
                Map.of(),
                new CalibrationSnapshot(1.2, 0.1, 0.1, 2.1, 1.9, 20L, 20L, 20L),
                0.0,
                0.0,
                0.0,
                true,
                "",
                0.0,
                0.0);
    }

    private CompactBenchmarkCase benchmarkCase(String regime,
                                               double compactCompletion,
                                               double omegaCompletion,
                                               double compactOnTime,
                                               double omegaOnTime,
                                               double compactDeadheadKm,
                                               double omegaDeadheadKm,
                                               double compactEmptyKm,
                                               double omegaEmptyKm,
                                               double compactPostDropHit,
                                               double omegaPostDropHit) {
        return new CompactBenchmarkCase(
                2026041101L,
                regime,
                report("baseline", compactCompletion, compactOnTime, compactDeadheadKm, compactEmptyKm, compactPostDropHit),
                report("compact", compactCompletion, compactOnTime, compactDeadheadKm, compactEmptyKm, compactPostDropHit),
                report("omega", omegaCompletion, omegaOnTime, omegaDeadheadKm, omegaEmptyKm, omegaPostDropHit),
                "snapshot",
                true,
                List.of(),
                Map.of(),
                Map.of("RUNTIME_FALLBACK", 1),
                0,
                0,
                0,
                Map.of(),
                new CalibrationSnapshot(1.0, 0.1, 0.1, 2.0, 1.5, 20L, 20L, 20L),
                0.0,
                0.0,
                0.0);
    }

    private RunReport report(String runId,
                             double completionRate,
                             double onTimeRate,
                             double deadheadPerCompletedOrderKm,
                             double expectedPostCompletionEmptyKm,
                             double postDropOrderHitRate) {
        return new RunReport(
                runId,
                runId,
                1L,
                Instant.parse("2026-04-12T09:00:00Z"),
                Instant.parse("2026-04-12T09:10:00Z"),
                10L,
                10,
                5,
                completionRate,
                onTimeRate,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0.0,
                0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0.0,
                0.0,
                0.0,
                expectedPostCompletionEmptyKm,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                deadheadPerCompletedOrderKm,
                0.0,
                postDropOrderHitRate,
                0.0,
                0.0,
                0.0,
                DispatchStageBreakdown.empty(),
                LatencyBreakdown.empty(),
                IntelligenceScorecard.empty(),
                ScenarioAcceptanceResult.empty(),
                "instant",
                Map.of(),
                ForecastCalibrationSummary.empty(),
                DispatchRecoveryDecomposition.empty());
    }
}
