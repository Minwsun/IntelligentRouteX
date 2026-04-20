package com.routechain.v2.decision;

import java.util.List;
import java.util.Map;

public final class LlmStageScheduler {
    private final NineRouterResponsesClient client;

    public LlmStageScheduler(NineRouterResponsesClient client) {
        this.client = client;
    }

    public DecisionStageOutputV1 evaluate(DecisionStageInputV1 input) {
        long startedAt = System.nanoTime();
        NineRouterResponsesClient.LlmInvocationResult result = client.invoke(input, input.stageName().requestedEffort());
        List<String> selectedIds = extractSelectedIds(result.parsedOutput());
        Map<String, Object> assessments = extractAssessments(result.parsedOutput());
        return new DecisionStageOutputV1(
                "stage-output-v1",
                input.traceId(),
                input.runId(),
                input.tickId(),
                input.stageName(),
                DecisionBrainType.LLM,
                "gpt-5.4",
                assessments,
                selectedIds,
                new DecisionStageMetaV1(
                        "decision-stage-meta/v1",
                        elapsedMs(startedAt),
                        0.75,
                        false,
                        null,
                        true,
                        "llm",
                        result.requestedEffort(),
                        result.appliedEffort(),
                        result.tokenUsage(),
                        result.retryCount(),
                        result.rawResponseHash()));
    }

    private List<String> extractSelectedIds(Map<String, Object> parsedOutput) {
        Object selectedIds = parsedOutput.get("selectedIds");
        if (selectedIds instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, Object> extractAssessments(Map<String, Object> parsedOutput) {
        Object assessments = parsedOutput.get("assessments");
        if (assessments instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> converted = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return converted;
        }
        return Map.of();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
