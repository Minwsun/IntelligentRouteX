package com.routechain.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Produces a compact structured prompt to reduce token usage on the free tier.
 */
public final class GroqPromptCompressor {

    public record CompressedPrompt(
            String systemPrompt,
            String userPrompt,
            int estimatedInputTokens
    ) {}

    public CompressedPrompt compress(LLMAdvisorRequest request,
                                     GroqRoutingPolicy.RoutingDecision decision,
                                     int maxCandidates) {
        List<LLMAdvisorRequest.CandidatePlanSummary> plans = request.candidatePlans().stream()
                .limit(Math.max(1, maxCandidates))
                .toList();

        String systemPrompt = """
                You are RouteChain's routing shadow critic.
                Reply with a single JSON object only.
                Required keys:
                routeIntent, corridorPreference, pickupWaveComment,
                dropSequenceCritique, softLandingComment, riskFlags, confidence, reasoning.
                Keep each string concise. riskFlags must be a JSON array of strings.
                Never invent coordinates or polyline geometry.
                """;

        StringJoiner user = new StringJoiner("\n");
        user.add("runId=" + safe(request.runId()));
        user.add("traceId=" + safe(request.traceId()));
        user.add("driverId=" + safe(request.driverId()));
        user.add("requestClass=" + safe(decision.requestClass().name()));
        user.add("executionProfile=" + safe(request.executionProfile()));
        user.add("activePolicy=" + safe(request.activePolicy()));
        user.add("stressRegime=" + safe(request.stressRegime()));
        user.add("context=" + summarizeFeatures(request.contextFeatures()));
        for (int i = 0; i < plans.size(); i++) {
            LLMAdvisorRequest.CandidatePlanSummary plan = plans.get(i);
            user.add(String.format(Locale.US,
                    "plan[%d]={selected=%s,bundle=%d,score=%.3f,onTime=%.3f,corridor=%.3f,landing=%.3f,emptyKm=%.3f,reason=\"%s\"}",
                    i,
                    plan.selected(),
                    plan.bundleSize(),
                    plan.totalScore(),
                    plan.onTimeProbability(),
                    plan.deliveryCorridorScore(),
                    plan.lastDropLandingScore(),
                    plan.expectedPostCompletionEmptyKm(),
                    sanitizeReason(plan.explanation())));
        }
        int estimated = request.estimatedInputTokens() > 0
                ? request.estimatedInputTokens()
                : estimateTokens(systemPrompt, user.toString());
        return new CompressedPrompt(systemPrompt, user.toString(), estimated);
    }

    public int estimateTokens(String... chunks) {
        int chars = Arrays.stream(chunks)
                .filter(chunk -> chunk != null)
                .mapToInt(String::length)
                .sum();
        return Math.max(32, chars / 4);
    }

    private String summarizeFeatures(double[] features) {
        if (features == null || features.length == 0) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        int limit = Math.min(features.length, 10);
        for (int i = 0; i < limit; i++) {
            joiner.add(String.format(Locale.US, "%.3f", features[i]));
        }
        if (features.length > limit) {
            joiner.add("...");
        }
        return joiner.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sanitizeReason(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String collapsed = value.replace('"', '\'').replace('\n', ' ').trim();
        return collapsed.length() <= 160 ? collapsed : collapsed.substring(0, 157) + "...";
    }
}
