package com.routechain.v2.decision;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.util.EnumSet;

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
        DecisionRuntimeMode runtimeMode = DecisionRuntimeMode.fromMode(properties.getDecision().getMode());
        DecisionBrainType requestedType = DecisionBrainType.fromMode(properties.getDecision().getMode());
        return switch (requestedType) {
            case LEGACY -> new ResolvedDecisionBrain(
                    requestedType,
                    DecisionBrainType.LEGACY,
                    legacyMlBrain,
                    legacyMlBrain,
                    false,
                    null,
                    runtimeMode,
                    EnumSet.noneOf(DecisionStageName.class));
            case STUDENT -> new ResolvedDecisionBrain(
                    requestedType,
                    DecisionBrainType.STUDENT,
                    studentBrain,
                    legacyMlBrain,
                    false,
                    null,
                    runtimeMode,
                    EnumSet.noneOf(DecisionStageName.class));
            case LLM -> resolveLlm(requestedType, runtimeMode);
        };
    }

    private ResolvedDecisionBrain resolveLlm(DecisionBrainType requestedType, DecisionRuntimeMode runtimeMode) {
        String apiKey = System.getenv(properties.getDecision().getLlm().getApiKeyEnv());
        if (apiKey == null || apiKey.isBlank()) {
            if (!properties.getDecision().isFallbackToLegacy()) {
                throw new IllegalStateException("LLM mode requires env " + properties.getDecision().getLlm().getApiKeyEnv());
            }
            return new ResolvedDecisionBrain(
                    requestedType,
                    DecisionBrainType.LEGACY,
                    legacyMlBrain,
                    legacyMlBrain,
                    true,
                    "llm-api-key-missing",
                    runtimeMode,
                    EnumSet.noneOf(DecisionStageName.class));
        }
        EnumSet<DecisionStageName> authoritativeStages = EnumSet.noneOf(DecisionStageName.class);
        for (String stageWireName : properties.getDecision().getAuthoritativeStages()) {
            authoritativeStages.add(DecisionStageName.fromWire(stageWireName));
        }
        return new ResolvedDecisionBrain(
                requestedType,
                DecisionBrainType.LLM,
                llmBrain,
                legacyMlBrain,
                false,
                null,
                runtimeMode,
                authoritativeStages);
    }
}
