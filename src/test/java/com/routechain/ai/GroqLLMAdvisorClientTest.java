package com.routechain.ai;

import com.routechain.infra.AdminQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqLLMAdvisorClientTest {

    @Test
    void shouldUseGroqWhenRemoteCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        GroqLLMAdvisorClient client = newClient((url, apiKey, modelId, systemPrompt, userPrompt, maxOutputTokens, timeoutMs) -> {
            calls.incrementAndGet();
            return new GroqTransport.GroqTransportResult(
                    200,
                    """
                    {"choices":[{"message":{"content":"{\\"routeIntent\\":\\"preserve corridor\\",\\"corridorPreference\\":\\"keep straight corridor\\",\\"pickupWaveComment\\":\\"wave is valid\\",\\"dropSequenceCritique\\":\\"sequence is corridor-safe\\",\\"softLandingComment\\":\\"landing is good\\",\\"riskFlags\\":[\\"low-risk\\"],\\"confidence\\":0.79,\\"reasoning\\":\\"structured shadow critique\\"}"}}],"usage":{"total_tokens":155}}
                    """,
                    java.util.Map.of(),
                    42L
            );
        });

        LLMAdvisorResponse response = client.advise(request(3, 0.03, true));

        assertEquals(1, calls.get());
        assertEquals("groq", response.provider());
        assertEquals("groq/compound-mini", response.modelId());
        assertEquals(LLMRequestClass.SHADOW_FAST, response.requestClass());
        assertFalse(response.fallbackApplied());
        assertEquals(42L, response.latencyMs());
    }

    @Test
    void shouldFallbackOfflineWhenRemoteFailsAcrossCascade() {
        AtomicInteger calls = new AtomicInteger();
        GroqLLMAdvisorClient client = newClient((url, apiKey, modelId, systemPrompt, userPrompt, maxOutputTokens, timeoutMs) -> {
            calls.incrementAndGet();
            int status = "groq/compound-mini".equals(modelId) ? 429 : 503;
            return new GroqTransport.GroqTransportResult(status, "{\"error\":\"unavailable\"}", java.util.Map.of(), 18L);
        });

        LLMAdvisorResponse response = client.advise(request(3, 0.02, true));

        assertEquals(2, calls.get());
        assertEquals("offline", response.provider());
        assertTrue(response.fallbackApplied());
        assertTrue(response.fallbackReason().contains("http-429"));
        assertTrue(response.fallbackChain().contains("groq/compound-mini"));
    }

    @Test
    void shouldStayOfflineWhenLocalBudgetGateRejectsRemoteUse() {
        AtomicInteger calls = new AtomicInteger();
        GroqLLMAdvisorClient client = newClient((url, apiKey, modelId, systemPrompt, userPrompt, maxOutputTokens, timeoutMs) -> {
            calls.incrementAndGet();
            return new GroqTransport.GroqTransportResult(200, "{}", java.util.Map.of(), 1L);
        });

        LLMAdvisorResponse response = client.advise(request(1, 0.25, false));

        assertEquals(0, calls.get());
        assertEquals("offline", response.provider());
        assertEquals("local-budget-gate", response.fallbackReason());
        assertTrue(response.fallbackApplied());
    }

    @Test
    void shouldFallbackOfflineOnMalformedPayload() {
        GroqLLMAdvisorClient client = newClient((url, apiKey, modelId, systemPrompt, userPrompt, maxOutputTokens, timeoutMs) ->
                new GroqTransport.GroqTransportResult(
                        200,
                        "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}],\"usage\":{\"total_tokens\":101}}",
                        java.util.Map.of(),
                        11L));

        LLMAdvisorResponse response = client.advise(request(3, 0.01, true));

        assertEquals("offline", response.provider());
        assertTrue(response.fallbackApplied());
        assertTrue(response.fallbackReason().contains("schema"));
    }

    private GroqLLMAdvisorClient newClient(GroqTransport transport) {
        GroqRuntimeConfig config = new GroqRuntimeConfig(
                true,
                "gsk_test_valid_key_123456789012345",
                "SHADOW",
                "https://api.groq.com/openai/v1/chat/completions",
                350,
                4,
                "FREE_TIER_BALANCED"
        );
        return new GroqLLMAdvisorClient(
                config,
                new GroqRoutingPolicy(GroqModelCatalog.freeTierDefaults()),
                new GroqPromptCompressor(),
                new GroqQuotaTracker(),
                new GroqCircuitBreaker(),
                transport,
                new OfflineFallbackLLMAdvisorClient(),
                status -> { }
        );
    }

    private LLMAdvisorRequest request(int bundleSize, double scoreGap, boolean bundleSelected) {
        double topScore = 1.00;
        double nextScore = topScore - scoreGap;
        return new LLMAdvisorRequest(
                "run-1",
                "trace-1",
                "driver-1",
                "MAINLINE_REALISTIC",
                "policy-x",
                "NORMAL",
                new double[]{0.22, 0.33, 0.44},
                List.of(
                        new LLMAdvisorRequest.CandidatePlanSummary(
                                "trace-1",
                                bundleSize,
                                topScore,
                                0.91,
                                0.74,
                                0.69,
                                1.2,
                                bundleSelected,
                                "selected plan"),
                        new LLMAdvisorRequest.CandidatePlanSummary(
                                "trace-2",
                                Math.max(1, bundleSize - 1),
                                nextScore,
                                0.88,
                                0.66,
                                0.61,
                                1.3,
                                false,
                                "second plan")),
                LLMRequestClass.SHADOW_FAST,
                120,
                false
        );
    }
}
