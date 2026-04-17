package com.routechain.v2.scenario;

import com.routechain.v2.SchemaVersioned;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record ScenarioEvaluationSummary(
        String schemaVersion,
        int proposalCount,
        int scenarioEvaluationCount,
        int appliedScenarioCount,
        Map<ScenarioType, Integer> scenarioCounts,
        Map<ScenarioType, Integer> appliedScenarioCounts,
        List<String> degradeReasons) implements SchemaVersioned {

    public static ScenarioEvaluationSummary empty() {
        return new ScenarioEvaluationSummary(
                "scenario-evaluation-summary/v1",
                0,
                0,
                0,
                new EnumMap<>(ScenarioType.class),
                new EnumMap<>(ScenarioType.class),
                List.of());
    }
}
