package com.routechain.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Known Groq free-tier model limits used by the local router.
 */
public final class GroqModelCatalog {

    public record ModelSpec(
            String modelId,
            int rpm,
            int rpd,
            int tpm,
            int tpd
    ) {}

    private final Map<String, ModelSpec> specs;

    private GroqModelCatalog(Map<String, ModelSpec> specs) {
        this.specs = Map.copyOf(specs);
    }

    public static GroqModelCatalog freeTierDefaults() {
        Map<String, ModelSpec> specs = new LinkedHashMap<>();
        register(specs, "groq/compound-mini", 30, 250, 70_000, Integer.MAX_VALUE);
        register(specs, "groq/compound", 30, 250, 70_000, Integer.MAX_VALUE);
        register(specs, "llama-3.1-8b-instant", 30, 14_400, 6_000, 500_000);
        register(specs, "llama-3.3-70b-versatile", 30, 1_000, 12_000, 100_000);
        register(specs, "meta-llama/llama-4-scout-17b-16e-instruct", 30, 1_000, 30_000, 500_000);
        register(specs, "moonshotai/kimi-k2-instruct", 60, 1_000, 10_000, 300_000);
        register(specs, "moonshotai/kimi-k2-instruct-0905", 60, 1_000, 10_000, 300_000);
        register(specs, "qwen/qwen3-32b", 60, 1_000, 6_000, 500_000);
        register(specs, "openai/gpt-oss-20b", 30, 1_000, 8_000, 200_000);
        register(specs, "openai/gpt-oss-120b", 30, 1_000, 8_000, 200_000);
        register(specs, "meta-llama/llama-prompt-guard-2-22m", 30, 14_400, 15_000, 500_000);
        return new GroqModelCatalog(specs);
    }

    public ModelSpec require(String modelId) {
        ModelSpec spec = specs.get(modelId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown Groq model: " + modelId);
        }
        return spec;
    }

    private static void register(Map<String, ModelSpec> specs,
                                 String modelId,
                                 int rpm,
                                 int rpd,
                                 int tpm,
                                 int tpd) {
        specs.put(modelId, new ModelSpec(modelId, rpm, rpd, tpm, tpd));
    }
}
