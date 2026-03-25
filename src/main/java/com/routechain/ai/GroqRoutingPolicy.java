package com.routechain.ai;

import java.util.List;

/**
 * Selects a model cascade and latency budget for a given request class.
 */
public final class GroqRoutingPolicy {

    public record RoutingDecision(
            LLMRequestClass requestClass,
            List<GroqModelCatalog.ModelSpec> cascade,
            int maxOutputTokens,
            int timeoutMs
    ) {}

    private final GroqModelCatalog catalog;

    public GroqRoutingPolicy(GroqModelCatalog catalog) {
        this.catalog = catalog;
    }

    public RoutingDecision route(LLMAdvisorRequest request, GroqRuntimeConfig config) {
        LLMRequestClass requestClass = request.requestClass() == null
                ? (request.operatorInitiated() ? LLMRequestClass.OPERATOR_FREE_TEXT : LLMRequestClass.SHADOW_FAST)
                : request.requestClass();
        return switch (requestClass) {
            case SHADOW_FAST -> new RoutingDecision(
                    requestClass,
                    List.of(
                            catalog.require("groq/compound-mini"),
                            catalog.require("llama-3.1-8b-instant")),
                    220,
                    Math.min(config.timeoutMs(), 350));
            case ADVISORY_HIGH_QUALITY -> new RoutingDecision(
                    requestClass,
                    List.of(
                            catalog.require("meta-llama/llama-4-scout-17b-16e-instruct"),
                            catalog.require("moonshotai/kimi-k2-instruct"),
                            catalog.require("groq/compound-mini")),
                    320,
                    Math.max(900, config.timeoutMs()));
            case REPLAY_BATCH -> new RoutingDecision(
                    requestClass,
                    List.of(
                            catalog.require("meta-llama/llama-4-scout-17b-16e-instruct"),
                            catalog.require("qwen/qwen3-32b"),
                            catalog.require("openai/gpt-oss-20b")),
                    420,
                    Math.max(2_500, config.timeoutMs() * 2));
            case OPERATOR_FREE_TEXT -> new RoutingDecision(
                    requestClass,
                    List.of(
                            catalog.require("groq/compound-mini"),
                            catalog.require("meta-llama/llama-prompt-guard-2-22m")),
                    260,
                    Math.max(1_200, config.timeoutMs()));
        };
    }
}
