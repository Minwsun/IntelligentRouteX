package com.routechain.v2.decision;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class LlmStageScheduler {
    private final NineRouterResponsesClient client;

    public LlmStageScheduler(NineRouterResponsesClient client) {
        this.client = client;
    }

    public DecisionStageOutputV1 evaluate(DecisionStageInputV1 input) {
        long startedAt = System.nanoTime();
        List<DecisionStageInputV1> shards = shardInputs(input);
        List<NineRouterResponsesClient.LlmInvocationResult> results = invokeShards(shards, input.stageName());
        List<String> selectedIds = mergeSelectedIds(results);
        Map<String, Object> assessments = mergeAssessments(results);
        NineRouterResponsesClient.LlmInvocationResult representative = results.isEmpty()
                ? new NineRouterResponsesClient.LlmInvocationResult(
                Map.of("selectedIds", List.of(), "assessments", Map.of()),
                input.stageName().requestedEffort().wireValue(),
                input.stageName().requestedEffort().wireValue(),
                Map.of(),
                0,
                "",
                "gpt-5.4")
                : results.getFirst();
        return new DecisionStageOutputV1(
                "stage-output-v1",
                input.traceId(),
                input.runId(),
                input.tickId(),
                input.stageName(),
                DecisionBrainType.LLM,
                representative.providerModel(),
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
                        representative.requestedEffort(),
                        representative.appliedEffort(),
                        mergeTokenUsage(results),
                        results.stream().mapToInt(NineRouterResponsesClient.LlmInvocationResult::retryCount).sum(),
                        representative.rawResponseHash()));
    }

    private List<DecisionStageInputV1> shardInputs(DecisionStageInputV1 input) {
        Object topIdsRaw = input.candidateSet().get("topIds");
        if (!(topIdsRaw instanceof List<?> topIds) || topIds.size() <= shardBudget(input.stageName())) {
            return List.of(input);
        }
        java.util.List<DecisionStageInputV1> shards = new java.util.ArrayList<>();
        java.util.List<String> values = topIds.stream().map(String::valueOf).toList();
        int shardSize = shardBudget(input.stageName());
        for (int index = 0; index < values.size(); index += shardSize) {
            List<String> shardIds = values.subList(index, Math.min(values.size(), index + shardSize));
            Map<String, Object> shardCandidateSet = new LinkedHashMap<>(input.candidateSet());
            shardCandidateSet.put("topIds", List.copyOf(shardIds));
            shards.add(new DecisionStageInputV1(
                    input.schemaVersion(),
                    input.traceId(),
                    input.runId(),
                    input.tickId(),
                    input.stageName(),
                    input.dispatchContext(),
                    shardCandidateSet,
                    input.constraints(),
                    input.objectiveWeights(),
                    input.upstreamRefs()));
        }
        return List.copyOf(shards);
    }

    private List<NineRouterResponsesClient.LlmInvocationResult> invokeShards(List<DecisionStageInputV1> shards,
                                                                             DecisionStageName stageName) {
        if (shards.size() == 1) {
            return List.of(client.invoke(shards.getFirst(), stageName.requestedEffort()));
        }
        int concurrency = Math.min(shards.size(), maxConcurrency(stageName));
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Callable<NineRouterResponsesClient.LlmInvocationResult>> tasks = shards.stream()
                    .map(shard -> (Callable<NineRouterResponsesClient.LlmInvocationResult>) () -> client.invoke(shard, shard.stageName().requestedEffort()))
                    .toList();
            List<Future<NineRouterResponsesClient.LlmInvocationResult>> futures = executor.invokeAll(tasks);
            java.util.List<NineRouterResponsesClient.LlmInvocationResult> results = new java.util.ArrayList<>();
            for (Future<NineRouterResponsesClient.LlmInvocationResult> future : futures) {
                results.add(future.get());
            }
            return List.copyOf(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("provider-timeout", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("provider-http-error", cause);
        }
    }

    private List<String> mergeSelectedIds(List<NineRouterResponsesClient.LlmInvocationResult> results) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (NineRouterResponsesClient.LlmInvocationResult result : results) {
            merged.addAll(extractSelectedIds(result.parsedOutput()));
        }
        return List.copyOf(merged);
    }

    private Map<String, Object> mergeAssessments(List<NineRouterResponsesClient.LlmInvocationResult> results) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        int shardIndex = 0;
        for (NineRouterResponsesClient.LlmInvocationResult result : results) {
            merged.put("shard-" + shardIndex++, extractAssessments(result.parsedOutput()));
        }
        return Map.copyOf(merged);
    }

    private Map<String, Object> mergeTokenUsage(List<NineRouterResponsesClient.LlmInvocationResult> results) {
        long inputTokens = 0L;
        long outputTokens = 0L;
        long totalTokens = 0L;
        for (NineRouterResponsesClient.LlmInvocationResult result : results) {
            inputTokens += longValue(result.tokenUsage(), "inputTokens");
            outputTokens += longValue(result.tokenUsage(), "outputTokens");
            totalTokens += longValue(result.tokenUsage(), "totalTokens");
        }
        return Map.of(
                "inputTokens", inputTokens,
                "outputTokens", outputTokens,
                "totalTokens", totalTokens);
    }

    private long longValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
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

    private int shardBudget(DecisionStageName stageName) {
        return switch (stageName) {
            case PAIR_BUNDLE -> 12;
            case ANCHOR -> 6;
            case DRIVER -> 8;
            case ROUTE_GENERATION, ROUTE_CRITIQUE -> 4;
            case SCENARIO, FINAL_SELECTION -> 3;
            case OBSERVATION_PACK, SAFETY_EXECUTE -> Integer.MAX_VALUE;
        };
    }

    private int maxConcurrency(DecisionStageName stageName) {
        return switch (stageName) {
            case PAIR_BUNDLE -> 4;
            case ANCHOR, DRIVER -> 3;
            case ROUTE_CRITIQUE, SCENARIO -> 2;
            case ROUTE_GENERATION, FINAL_SELECTION -> 1;
            case OBSERVATION_PACK, SAFETY_EXECUTE -> 1;
        };
    }
}
