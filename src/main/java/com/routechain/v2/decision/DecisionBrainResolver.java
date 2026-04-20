package com.routechain.v2.decision;

import com.routechain.config.RouteChainDispatchV2Properties;

public final class DecisionBrainResolver {
    private final RouteChainDispatchV2Properties properties;
    private final LegacyMlBrain legacyMlBrain;
    private final LlmBrain llmBrain;
    private final StudentBrain studentBrain;

    public DecisionBrainResolver(RouteChainDispatchV2Properties properties,
                                 LegacyMlBrain legacyMlBrain,
                                 LlmBrain llmBrain,
                                 StudentBrain studentBrain) {
        this.properties = properties;
        this.legacyMlBrain = legacyMlBrain;
        this.llmBrain = llmBrain;
        this.studentBrain = studentBrain;
    }

    public ResolvedDecisionBrain resolve() {
        DecisionBrainType requestedType = DecisionBrainType.fromMode(properties.getDecision().getMode());
        return switch (requestedType) {
            case LEGACY -> new ResolvedDecisionBrain(requestedType, DecisionBrainType.LEGACY, legacyMlBrain, false, null);
            case STUDENT -> new ResolvedDecisionBrain(requestedType, DecisionBrainType.STUDENT, studentBrain, false, null);
            case LLM -> resolveLlm(requestedType);
        };
    }

    private ResolvedDecisionBrain resolveLlm(DecisionBrainType requestedType) {
        String apiKey = System.getenv(properties.getDecision().getLlm().getApiKeyEnv());
        if (apiKey == null || apiKey.isBlank()) {
            if (!properties.getDecision().isFallbackToLegacy()) {
                throw new IllegalStateException("LLM mode requires env " + properties.getDecision().getLlm().getApiKeyEnv());
            }
            return new ResolvedDecisionBrain(requestedType, DecisionBrainType.LEGACY, legacyMlBrain, true, "llm-api-key-missing");
        }
        return new ResolvedDecisionBrain(requestedType, DecisionBrainType.LLM, llmBrain, false, null);
    }
}
