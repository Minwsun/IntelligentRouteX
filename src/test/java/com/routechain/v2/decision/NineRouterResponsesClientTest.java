package com.routechain.v2.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NineRouterResponsesClientTest {

    @Test
    void downgradesXhighToHighWhenProviderRejectsIt() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getDecision().getLlm().setApiKeyEnv("PATH");
        AtomicInteger calls = new AtomicInteger();
        NineRouterResponsesClient client = new NineRouterResponsesClient(
                properties.getDecision().getLlm(),
                (baseUrl, apiKey, timeout, requestBody, objectMapper) -> {
                    int attempt = calls.incrementAndGet();
                    if (attempt == 1) {
                        return new NineRouterResponsesClient.TransportResponse(400, "{\"error\":\"unsupported effort\"}");
                    }
                    return new NineRouterResponsesClient.TransportResponse(
                            200,
                            "{\"output\":[{\"content\":[{\"text\":\"{\\\"selectedIds\\\":[\\\"proposal-1\\\"],\\\"assessments\\\":{\\\"score\\\":0.91}}\"}]}],\"usage\":{\"input_tokens\":100,\"output_tokens\":25}}");
                },
                JsonMapper.builder().findAndAddModules().build());

        NineRouterResponsesClient.LlmInvocationResult result = client.invoke(
                new DecisionStageInputV1(
                        "stage-input-v1",
                        "trace-1",
                        "run-1",
                        Instant.parse("2026-04-20T00:00:00Z").toString(),
                        DecisionStageName.FINAL_SELECTION,
                        java.util.Map.of(),
                        java.util.Map.of("topIds", java.util.List.of("proposal-1")),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.List.of()),
                DecisionEffort.XHIGH);

        assertEquals("xhigh", result.requestedEffort());
        assertEquals("high", result.appliedEffort());
        assertEquals(1, result.retryCount());
        assertEquals("proposal-1", ((java.util.List<?>) result.parsedOutput().get("selectedIds")).getFirst());
    }
}
