package com.routechain.v2.decision;

import com.routechain.v2.SchemaVersioned;

import java.util.List;
import java.util.Map;

public record DecisionStageOutputV1(
        String schemaVersion,
        String traceId,
        String runId,
        String tickId,
        DecisionStageName stageName,
        DecisionBrainType brainType,
        String providerModel,
        Map<String, Object> assessments,
        List<String> selectedIds,
        DecisionStageMetaV1 meta) implements SchemaVersioned {

    public DecisionStageOutputV1 {
        assessments = assessments == null ? Map.of() : Map.copyOf(assessments);
        selectedIds = selectedIds == null ? List.of() : List.copyOf(selectedIds);
    }
}
