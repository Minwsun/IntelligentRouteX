package com.routechain.v2.decision;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record ResolvedDecisionBrain(
        DecisionBrainType requestedType,
        DecisionBrainType appliedType,
        DecisionBrain brain,
        DecisionBrain legacyBrain,
        boolean fallbackUsed,
        String fallbackReason,
        DecisionRuntimeMode runtimeMode,
        Set<DecisionStageName> authoritativeStages) {

    public ResolvedDecisionBrain {
        authoritativeStages = authoritativeStages == null
                ? EnumSet.noneOf(DecisionStageName.class)
                : EnumSet.copyOf(authoritativeStages);
    }

    public boolean shouldEvaluateWithLlm(DecisionStageName stageName) {
        if (appliedType != DecisionBrainType.LLM || stageName == null || !stageName.supportsLlmDecision()) {
            return false;
        }
        return switch (runtimeMode) {
            case LLM_SHADOW -> true;
            case LLM, LLM_AUTHORITATIVE, HYBRID -> authoritativeStages.contains(stageName);
            case LEGACY, STUDENT -> false;
        };
    }

    public boolean shouldApplyAuthoritatively(DecisionStageName stageName) {
        if (appliedType != DecisionBrainType.LLM || stageName == null || !stageName.supportsLlmDecision()) {
            return false;
        }
        return switch (runtimeMode) {
            case LLM, LLM_AUTHORITATIVE, HYBRID -> authoritativeStages.contains(stageName);
            case LEGACY, LLM_SHADOW, STUDENT -> false;
        };
    }

    public List<String> authoritativeStageWireNames() {
        return authoritativeStages.stream().map(DecisionStageName::wireName).sorted().toList();
    }

    public DecisionBrain loggingBrainForStage(DecisionStageName stageName) {
        if (requestedType == DecisionBrainType.STUDENT || appliedType == DecisionBrainType.STUDENT) {
            return brain;
        }
        if (shouldEvaluateWithLlm(stageName)) {
            return brain;
        }
        return legacyBrain;
    }
}
