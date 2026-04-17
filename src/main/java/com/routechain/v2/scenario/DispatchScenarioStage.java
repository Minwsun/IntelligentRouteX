package com.routechain.v2.scenario;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchScenarioStage(
        String schemaVersion,
        List<ScenarioEvaluation> scenarioEvaluations,
        List<RobustUtility> robustUtilities,
        ScenarioEvaluationSummary scenarioEvaluationSummary,
        List<String> degradeReasons) implements SchemaVersioned {
}
