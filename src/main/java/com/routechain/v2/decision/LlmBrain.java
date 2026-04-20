package com.routechain.v2.decision;

import java.util.Map;

public final class LlmBrain implements DecisionBrain {
    private final LlmStageScheduler llmStageScheduler;
    private final LegacyMlBrain legacyMlBrain;
    private final DecisionStageLogger decisionStageLogger;

    public LlmBrain(LlmStageScheduler llmStageScheduler,
                    LegacyMlBrain legacyMlBrain,
                    DecisionStageLogger decisionStageLogger) {
        this.llmStageScheduler = llmStageScheduler;
        this.legacyMlBrain = legacyMlBrain;
        this.decisionStageLogger = decisionStageLogger;
    }

    @Override
    public DecisionStageOutputV1 evaluateStage(DecisionStageInputV1 input) {
        if (!input.stageName().llmPhaseOneEnabled()) {
            return fallback(input, "llm-stage-not-enabled-phase-1");
        }
        try {
            decisionStageLogger.writeFamily("llm_context_selection_trace", input.traceId(), input.stageName().wireName(), input);
            DecisionStageOutputV1 output = llmStageScheduler.evaluate(input);
            decisionStageLogger.writeFamily("llm_usage_meta", input.traceId(), input.stageName().wireName(), output.meta());
            return output;
        } catch (RuntimeException exception) {
            return fallback(input, exception.getMessage() == null ? "llm-stage-failed" : exception.getMessage());
        }
    }

    private DecisionStageOutputV1 fallback(DecisionStageInputV1 input, String fallbackReason) {
        DecisionStageOutputV1 legacy = legacyMlBrain.evaluateStage(input);
        decisionStageLogger.writeFamily("llm_request_meta", input.traceId(), input.stageName().wireName(), Map.of(
                "stageName", input.stageName().wireName(),
                "fallbackReason", fallbackReason));
        return new DecisionStageOutputV1(
                legacy.schemaVersion(),
                legacy.traceId(),
                legacy.runId(),
                legacy.tickId(),
                legacy.stageName(),
                legacy.brainType(),
                legacy.providerModel(),
                legacy.assessments(),
                legacy.selectedIds(),
                new DecisionStageMetaV1(
                        "decision-stage-meta/v1",
                        legacy.meta().latencyMs(),
                        legacy.meta().confidence(),
                        true,
                        fallbackReason,
                        legacy.meta().validationPassed(),
                        "legacy",
                        input.stageName().requestedEffort().wireValue(),
                        DecisionEffort.HIGH.wireValue(),
                        legacy.meta().tokenUsage(),
                        legacy.meta().retryCount(),
                        legacy.meta().rawResponseHash()));
    }
}
