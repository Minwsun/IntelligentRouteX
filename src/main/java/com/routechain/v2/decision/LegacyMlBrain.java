package com.routechain.v2.decision;

import java.util.List;
import java.util.Map;

public final class LegacyMlBrain implements DecisionBrain {

    @Override
    public DecisionStageOutputV1 evaluateStage(DecisionStageInputV1 input) {
        long startedAt = System.nanoTime();
        List<String> selectedIds = selectIds(input.candidateSet());
        return new DecisionStageOutputV1(
                "stage-output-v1",
                input.traceId(),
                input.runId(),
                input.tickId(),
                input.stageName(),
                DecisionBrainType.LEGACY,
                null,
                Map.of(
                        "candidateCount", selectedIds.size(),
                        "summaryKeys", input.candidateSet().keySet().stream().sorted().toList()),
                selectedIds,
                DecisionStageMetaV1.legacy(elapsedMs(startedAt)));
    }

    private List<String> selectIds(Map<String, Object> candidateSet) {
        Object topIds = candidateSet.get("topIds");
        if (topIds instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        Object ids = candidateSet.get("ids");
        if (ids instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
