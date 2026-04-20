package com.routechain.v2.decision;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionBrainResolverTest {

    @Test
    void fallsBackToLegacyWhenLlmApiKeyIsMissing() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getDecision().getLlm().setApiKeyEnv("IRX_TEST_MISSING_KEY");
        DecisionBrainResolver resolver = new DecisionBrainResolver(
                properties,
                new LegacyMlBrain(),
                new LlmBrain(
                        new LlmStageScheduler(new NineRouterResponsesClient(properties.getDecision().getLlm())),
                        new LegacyMlBrain(),
                        new DecisionStageLogger(properties)),
                new StudentBrain(new LegacyMlBrain()));

        ResolvedDecisionBrain resolved = resolver.resolve();

        assertEquals(DecisionBrainType.LLM, resolved.requestedType());
        assertEquals(DecisionBrainType.LEGACY, resolved.appliedType());
        assertTrue(resolved.fallbackUsed());
        assertEquals("llm-api-key-missing", resolved.fallbackReason());
    }
}
