package com.routechain.v2.scenario;

import com.routechain.v2.SchemaVersioned;

public record RobustUtility(
        String schemaVersion,
        String proposalId,
        double expectedValue,
        double worstCaseValue,
        double landingValue,
        double stabilityScore,
        double robustUtility,
        int scenarioCount,
        int appliedScenarioCount) implements SchemaVersioned {
}
