package com.routechain.v2.scenario;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.context.FreshnessMetadata;

import java.util.List;

public record DispatchScenarioStage(
        String schemaVersion,
        List<ScenarioEvaluation> scenarioEvaluations,
        List<RobustUtility> robustUtilities,
        ScenarioEvaluationSummary scenarioEvaluationSummary,
        FreshnessMetadata freshnessMetadata,
        List<DispatchStageLatency> stageLatencies,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}
