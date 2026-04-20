package com.routechain.v2.decision;

import com.routechain.v2.SchemaVersioned;

import java.util.List;
import java.util.Map;

public record DecisionStageInputV1(
        String schemaVersion,
        String traceId,
        String runId,
        String tickId,
        DecisionStageName stageName,
        Map<String, Object> dispatchContext,
        Map<String, Object> candidateSet,
        Map<String, Object> constraints,
        Map<String, Double> objectiveWeights,
        List<String> upstreamRefs) implements SchemaVersioned {

    public DecisionStageInputV1 {
        dispatchContext = dispatchContext == null ? Map.of() : Map.copyOf(dispatchContext);
        candidateSet = candidateSet == null ? Map.of() : Map.copyOf(candidateSet);
        constraints = constraints == null ? Map.of() : Map.copyOf(constraints);
        objectiveWeights = objectiveWeights == null ? Map.of() : Map.copyOf(objectiveWeights);
        upstreamRefs = upstreamRefs == null ? List.of() : List.copyOf(upstreamRefs);
    }
}
