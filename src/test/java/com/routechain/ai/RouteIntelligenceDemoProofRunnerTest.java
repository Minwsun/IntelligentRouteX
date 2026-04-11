package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteIntelligenceDemoProofRunnerTest {

    @Test
    void shouldExplainStrongClearCase() {
        ReplayCompareResult baselineToAdaptive = compare(
                1.6,
                -0.30,
                2.4,
                -0.20,
                -0.10,
                0.0,
                0.0,
                0.0
        );
        ReplayCompareResult staticToAdaptive = compare(
                0.9,
                -0.22,
                1.4,
                -0.12,
                -0.08,
                0.0,
                0.0,
                0.0
        );

        DemoProofCase demoCase = RouteIntelligenceDemoProofRunner.evaluateCase(
                "CLEAR",
                "instant-normal",
                42L,
                baselineToAdaptive,
                staticToAdaptive
        );

        assertTrue(demoCase.overallPass());
        assertTrue(demoCase.explanation().summaryParagraph().contains("CLEAR"));
    }

    @Test
    void shouldFailWeakDinnerPeakCase() {
        ReplayCompareResult baselineToAdaptive = compare(
                -0.3,
                0.10,
                -1.0,
                0.08,
                0.20,
                -2.0,
                0.0,
                0.0
        );
        ReplayCompareResult staticToAdaptive = compare(
                0.1,
                0.02,
                -0.5,
                0.01,
                0.05,
                -0.2,
                0.0,
                0.0
        );

        DemoProofCase demoCase = RouteIntelligenceDemoProofRunner.evaluateCase(
                "DINNER_PEAK_HCMC",
                "realistic-hcmc-dinner-peak-run0",
                42L,
                baselineToAdaptive,
                staticToAdaptive
        );

        assertFalse(demoCase.overallPass());
        assertFalse(demoCase.notes().isEmpty());
    }

    @Test
    void shouldExposeShadowProofExecution() {
        RouteIntelligenceDemoProofRunner.ProofCaseExecution execution =
                RouteIntelligenceDemoProofRunner.runProofCase("clear-smart-batch-win", "shadow");

        assertTrue(execution.caseId().contains("clear"));
        assertTrue(execution.policies().size() >= 3);
        assertTrue(execution.oracleSubset().mode().contains("ortools-shadow"));
    }

    private ReplayCompareResult compare(double overallGainPercent,
                                        double deadheadPerCompletedOrderKmDelta,
                                        double postDropOrderHitRateDelta,
                                        double expectedPostCompletionEmptyKmDelta,
                                        double lastDropGoodZoneRateDelta,
                                        double cancellationRateDelta,
                                        double holdOnlySelectionRateDelta,
                                        double realAssignmentRateDelta) {
        return new ReplayCompareResult(
                "baseline",
                "candidate",
                "scenario-a",
                "scenario-b",
                1.4,
                0.4,
                cancellationRateDelta,
                -1.0,
                0.02,
                1000.0,
                -0.2,
                -0.1,
                -20.0,
                0.0,
                0.08,
                lastDropGoodZoneRateDelta,
                expectedPostCompletionEmptyKmDelta,
                -0.3,
                -0.02,
                realAssignmentRateDelta,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                holdOnlySelectionRateDelta,
                realAssignmentRateDelta,
                -0.05,
                deadheadPerCompletedOrderKmDelta,
                -0.12,
                postDropOrderHitRateDelta,
                0.1,
                0.0,
                0.0,
                new LatencyBreakdownDelta(-5.0, -4.0, -3.0, -2.0, 0.0, 0.0, 0.0, 0.0, -40.0, -80.0, 0.1),
                new IntelligenceScorecardDelta(0.02, 0.03, 0.01, 0.01, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                new ScenarioAcceptanceDelta(true, true, true, true, true, true, true, true, true, true, ""),
                "instant",
                "instant",
                java.util.Map.of(),
                new ForecastCalibrationSummaryDelta(-0.2, -0.1, -0.05, 0.02),
                new DispatchRecoveryDecompositionDelta(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                overallGainPercent > 0.0 ? "AI_BETTER" : "BASELINE_BETTER",
                overallGainPercent
        );
    }
}
