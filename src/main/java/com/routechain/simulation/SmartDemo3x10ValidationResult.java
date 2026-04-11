package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

public record SmartDemo3x10ValidationResult(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        JavaFxDemoScenarioSpec scenario,
        RuntimeCorrectness runtimeCorrectness,
        ReplayCompareResult adaptiveVsLegacy,
        ReplayCompareResult adaptiveVsStatic,
        List<JavaFxPolicyComparison> policies,
        OracleAssessment oracleAssessment,
        BehaviorAssessment behaviorAssessment,
        String explanationSummary,
        JavaFxDemoVerdict verdict,
        List<String> notes
) {
    public SmartDemo3x10ValidationResult {
        policies = policies == null ? List.of() : List.copyOf(policies);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public record RuntimeCorrectness(
            boolean pass,
            int expectedDrivers,
            int actualDrivers,
            int expectedOrders,
            int actualOrders,
            int initialDriverCount,
            double demandMultiplier,
            String simulatedTime,
            String weatherProfile
    ) { }

    public record OracleAssessment(
            String mode,
            String leaderPolicyKey,
            double adaptiveGapToLeader,
            double legacyObjective,
            double adaptiveObjective,
            double staticObjective,
            String verdict
    ) { }

    public record BehaviorAssessment(
            boolean acceptedGoodBatch,
            boolean rejectedBadBatch,
            boolean preferredBetterLanding,
            boolean avoidedEmptyAfterDrop,
            int satisfiedBehaviorCount
    ) { }
}
