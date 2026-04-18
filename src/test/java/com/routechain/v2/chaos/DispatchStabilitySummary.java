package com.routechain.v2.chaos;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchStabilitySummary(
        String schemaVersion,
        String suiteType,
        int scenarioCount,
        int passedScenarioCount,
        int failedScenarioCount,
        List<DispatchStabilityScenarioOutcome> scenarioResults,
        List<String> failureSummaries) implements SchemaVersioned {
}
