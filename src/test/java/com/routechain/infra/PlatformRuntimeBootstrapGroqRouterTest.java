package com.routechain.infra;

import com.routechain.ai.GroqLLMAdvisorClient;
import com.routechain.ai.OfflineFallbackLLMAdvisorClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PlatformRuntimeBootstrapGroqRouterTest {

    @AfterEach
    void cleanup() {
        System.clearProperty("routechain.groq.enabled");
        System.clearProperty("routechain.groq.api-key");
        System.clearProperty("routechain.groq.mode");
        System.clearProperty("routechain.groq.timeout-ms");
        PlatformRuntimeBootstrap.refreshLlmRuntime();
    }

    @Test
    void shouldChooseGroqClientWhenConfigured() {
        System.setProperty("routechain.groq.enabled", "true");
        System.setProperty("routechain.groq.api-key", "gsk_test_valid_key_123456789012345");
        System.setProperty("routechain.groq.mode", "SHADOW");

        PlatformRuntimeBootstrap.refreshLlmRuntime();

        assertInstanceOf(GroqLLMAdvisorClient.class, PlatformRuntimeBootstrap.getLlmAdvisorClient());
        assertEquals("SHADOW", PlatformRuntimeBootstrap.getAdminQueryService().snapshot().llmMode());
        assertEquals("groq", PlatformRuntimeBootstrap.getAdminQueryService().snapshot().llmRuntimeStatus().provider());
    }

    @Test
    void shouldChooseOfflineFallbackWhenGroqIsMissing() {
        PlatformRuntimeBootstrap.refreshLlmRuntime();

        assertInstanceOf(OfflineFallbackLLMAdvisorClient.class, PlatformRuntimeBootstrap.getLlmAdvisorClient());
        assertEquals("OFFLINE", PlatformRuntimeBootstrap.getAdminQueryService().snapshot().llmMode());
        assertEquals("offline", PlatformRuntimeBootstrap.getAdminQueryService().snapshot().llmRuntimeStatus().provider());
    }
}
