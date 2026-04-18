package com.routechain.v2.certification;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchCertificationScenarioReport(
        String schemaVersion,
        String suiteName,
        String scenarioName,
        List<String> decisionStages,
        long coldTotalLatencyMs,
        long warmTotalLatencyMs,
        long hotTotalLatencyMs,
        List<String> reusedStageNames,
        long estimatedSavedMs,
        List<String> degradeReasons,
        List<String> correctnessMismatchReasons,
        boolean conflictFreeAssignments,
        List<String> budgetBreachedStageNames,
        boolean totalBudgetBreached,
        List<String> mlMetadataSources,
        List<String> liveMetadataSources,
        boolean passed) implements SchemaVersioned {
}
