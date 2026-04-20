package com.routechain.v2.decision;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.util.Map;

public final class LlmBrain implements DecisionBrain {
    private final LlmStageScheduler llmStageScheduler;
    private final LegacyMlBrain legacyMlBrain;
    private final DecisionStageLogger decisionStageLogger;
    private final RouteChainDispatchV2Properties.Llm properties;

    public LlmBrain(LlmStageScheduler llmStageScheduler,
                    RouteChainDispatchV2Properties.Llm properties,
                    LegacyMlBrain legacyMlBrain,
                    DecisionStageLogger decisionStageLogger) {
        this.llmStageScheduler = llmStageScheduler;
        this.properties = properties;
        this.legacyMlBrain = legacyMlBrain;
        this.decisionStageLogger = decisionStageLogger;
    }

    @Override
    public DecisionStageOutputV1 evaluateStage(DecisionStageInputV1 input) {
        if (!input.stageName().supportsLlmDecision()) {
            return fallback(input, "llm-stage-not-supported");
        }
        NineRouterResponsesClient.RuntimeConfiguration runtimeConfiguration = llmStageScheduler.runtimeConfiguration();
        decisionStageLogger.writeFamily("llm_request_meta", input.traceId(), input.stageName().wireName(), Map.of(
                "stageName", input.stageName().wireName(),
                "configuredModelFamily", runtimeConfiguration.configuredModelFamily(),
                "providerBaseUrl", runtimeConfiguration.baseUrl(),
                "providerWireApi", runtimeConfiguration.wireApi(),
                "provider", properties.getProvider()));
        try {
            decisionStageLogger.writeFamily("llm_context_selection_trace", input.traceId(), input.stageName().wireName(), input);
            DecisionStageOutputV1 output = llmStageScheduler.evaluate(input);
            decisionStageLogger.writeFamily("llm_usage_meta", input.traceId(), input.stageName().wireName(), output.meta());
            return output;
        } catch (NineRouterResponsesClient.NineRouterClientException exception) {
            return fallback(input, exception.failureReason(), Map.of(
                    "stageName", input.stageName().wireName(),
                    "configuredModelFamily", runtimeConfiguration.configuredModelFamily(),
                    "providerBaseUrl", runtimeConfiguration.baseUrl(),
                    "providerWireApi", runtimeConfiguration.wireApi(),
                    "provider", properties.getProvider(),
                    "details", exception.details()));
        } catch (RuntimeException exception) {
            return fallback(input, exception.getMessage() == null ? "llm-stage-failed" : exception.getMessage(), Map.of(
                    "stageName", input.stageName().wireName(),
                    "configuredModelFamily", runtimeConfiguration.configuredModelFamily(),
                    "providerBaseUrl", runtimeConfiguration.baseUrl(),
                    "providerWireApi", runtimeConfiguration.wireApi(),
                    "provider", properties.getProvider()));
        }
    }

    private DecisionStageOutputV1 fallback(DecisionStageInputV1 input, String fallbackReason) {
        return fallback(input, fallbackReason, Map.of(
                "stageName", input.stageName().wireName(),
                "configuredModelFamily", properties.getModel(),
                "providerBaseUrl", properties.getBaseUrl(),
                "providerWireApi", properties.getWireApi(),
                "provider", properties.getProvider()));
    }

    private DecisionStageOutputV1 fallback(DecisionStageInputV1 input,
                                           String fallbackReason,
                                           Map<String, Object> requestMeta) {
        DecisionStageOutputV1 legacy = legacyMlBrain.evaluateStage(input);
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>(requestMeta);
        payload.put("fallbackReason", fallbackReason);
        decisionStageLogger.writeFamily("llm_request_meta", input.traceId(), input.stageName().wireName(), Map.copyOf(payload));
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
                        input.stageName().requestedEffort().wireValue(),
                        legacy.meta().tokenUsage(),
                        legacy.meta().retryCount(),
                        legacy.meta().rawResponseHash()));
    }
}
