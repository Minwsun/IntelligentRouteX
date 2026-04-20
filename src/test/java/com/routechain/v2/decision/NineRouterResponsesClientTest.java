package com.routechain.v2.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NineRouterResponsesClientTest {

    @Test
    void resolvesNamespacedModelIdFromFamilyAndDowngradesEffort() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getDecision().getLlm().setApiKeyEnv("PATH");
        AtomicInteger postCalls = new AtomicInteger();
        NineRouterResponsesClient client = new NineRouterResponsesClient(
                properties.getDecision().getLlm(),
                new StubTransport(
                        "{\"data\":[{\"id\":\"gh/gpt-5.4\",\"root\":\"gpt-5.4\"},{\"id\":\"cx/gpt-5.4\",\"root\":\"gpt-5.4\"}]}",
                        (baseUrl, apiKey, timeout, requestBody, objectMapper) -> {
                            int attempt = postCalls.incrementAndGet();
                            if (attempt == 1) {
                                return new NineRouterResponsesClient.TransportResponse(400, "{\"error\":\"unsupported effort\"}");
                            }
                            assertEquals("cx/gpt-5.4", requestBody.path("model").asText());
                            return new NineRouterResponsesClient.TransportResponse(
                                    200,
                                    "{\"output\":[{\"content\":[{\"text\":\"{\\\"selectedIds\\\":[\\\"proposal-1\\\"],\\\"assessments\\\":{\\\"score\\\":0.91}}\"}]}],\"usage\":{\"input_tokens\":100,\"output_tokens\":25}}");
                        }),
                JsonMapper.builder().findAndAddModules().build());

        NineRouterResponsesClient.LlmInvocationResult result = client.invoke(stageInput(DecisionStageName.FINAL_SELECTION), DecisionEffort.XHIGH);

        assertEquals("gpt-5.4", result.configuredModelFamily());
        assertEquals("cx/gpt-5.4", result.providerModel());
        assertEquals("xhigh", result.requestedEffort());
        assertEquals("high", result.appliedEffort());
        assertEquals(1, result.retryCount());
        assertEquals("proposal-1", ((java.util.List<?>) result.parsedOutput().get("selectedIds")).getFirst());
        assertEquals("/v1/models", result.modelDiscoverySource());
    }

    @Test
    void honorsExactConfiguredNamespacedModelId() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getDecision().getLlm().setApiKeyEnv("PATH");
        properties.getDecision().getLlm().setModel("gh/gpt-5.4");
        NineRouterResponsesClient client = new NineRouterResponsesClient(
                properties.getDecision().getLlm(),
                new StubTransport(
                        "{\"data\":[{\"id\":\"gh/gpt-5.4\",\"root\":\"gpt-5.4\"},{\"id\":\"cx/gpt-5.4\",\"root\":\"gpt-5.4\"}]}",
                        (baseUrl, apiKey, timeout, requestBody, objectMapper) -> {
                            assertEquals("gh/gpt-5.4", requestBody.path("model").asText());
                            return new NineRouterResponsesClient.TransportResponse(
                                    200,
                                    "{\"output\":[{\"content\":[{\"text\":\"{\\\"selectedIds\\\":[\\\"proposal-1\\\"],\\\"assessments\\\":{}}\"}]}]}");
                        }),
                JsonMapper.builder().findAndAddModules().build());

        NineRouterResponsesClient.LlmInvocationResult result = client.invoke(stageInput(DecisionStageName.PAIR_BUNDLE), DecisionEffort.MEDIUM);

        assertEquals("gh/gpt-5.4", result.providerModel());
        assertEquals("gh/gpt-5.4", result.configuredModelFamily());
    }

    @Test
    void failsFastWhenConfiguredFamilyCannotBeResolved() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getDecision().getLlm().setApiKeyEnv("PATH");
        NineRouterResponsesClient client = new NineRouterResponsesClient(
                properties.getDecision().getLlm(),
                new StubTransport(
                        "{\"data\":[{\"id\":\"cx/gpt-5.2\",\"root\":\"gpt-5.2\"}]}",
                        (baseUrl, apiKey, timeout, requestBody, objectMapper) -> new NineRouterResponsesClient.TransportResponse(200, "{}")),
                JsonMapper.builder().findAndAddModules().build());

        NineRouterResponsesClient.NineRouterClientException exception = assertThrows(
                NineRouterResponsesClient.NineRouterClientException.class,
                () -> client.invoke(stageInput(DecisionStageName.PAIR_BUNDLE), DecisionEffort.MEDIUM));

        assertEquals("provider-model-unresolved", exception.failureReason());
        assertEquals("gpt-5.4", exception.details().get("configuredModelFamily"));
    }

    private DecisionStageInputV1 stageInput(DecisionStageName stageName) {
        return new DecisionStageInputV1(
                "stage-input-v1",
                "trace-1",
                "run-1",
                Instant.parse("2026-04-20T00:00:00Z").toString(),
                stageName,
                java.util.Map.of(),
                java.util.Map.of("topIds", java.util.List.of("proposal-1")),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.List.of());
    }

    private record StubTransport(
            String modelsBody,
            PostHandler postHandler) implements NineRouterResponsesClient.ResponsesTransport {

        @Override
        public NineRouterResponsesClient.TransportResponse get(String baseUrl,
                                                               String apiKey,
                                                               Duration timeout,
                                                               ObjectMapper objectMapper,
                                                               String relativePath) {
            assertEquals("/models", relativePath);
            return new NineRouterResponsesClient.TransportResponse(200, modelsBody);
        }

        @Override
        public NineRouterResponsesClient.TransportResponse post(String baseUrl,
                                                                String apiKey,
                                                                Duration timeout,
                                                                JsonNode requestBody,
                                                                ObjectMapper objectMapper) {
            return postHandler.handle(baseUrl, apiKey, timeout, requestBody, objectMapper);
        }
    }

    @FunctionalInterface
    private interface PostHandler {
        NineRouterResponsesClient.TransportResponse handle(String baseUrl,
                                                           String apiKey,
                                                           Duration timeout,
                                                           JsonNode requestBody,
                                                           ObjectMapper objectMapper);
    }
}
