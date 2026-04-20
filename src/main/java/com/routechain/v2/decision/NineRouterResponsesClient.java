package com.routechain.v2.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.config.RouteChainDispatchV2Properties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class NineRouterResponsesClient {
    private final RouteChainDispatchV2Properties.Llm properties;
    private final ResponsesTransport transport;
    private final ObjectMapper objectMapper;

    public NineRouterResponsesClient(RouteChainDispatchV2Properties.Llm properties) {
        this(properties, new DefaultResponsesTransport(), JsonMapper.builder().findAndAddModules().build());
    }

    NineRouterResponsesClient(RouteChainDispatchV2Properties.Llm properties,
                              ResponsesTransport transport,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    public LlmInvocationResult invoke(DecisionStageInputV1 input, DecisionEffort requestedEffort) {
        String apiKey = System.getenv(properties.getApiKeyEnv());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing API key in env " + properties.getApiKeyEnv());
        }
        DecisionEffort appliedEffort = requestedEffort == null ? DecisionEffort.MEDIUM : requestedEffort;
        int retryCount = 0;
        while (true) {
            JsonNode requestBody = buildRequestBody(input, appliedEffort);
            TransportResponse response = transport.post(
                    properties.getBaseUrl(),
                    apiKey,
                    properties.getTimeoutMs(),
                    requestBody,
                    objectMapper);
            if (response.statusCode() >= 400 && requestedEffort == DecisionEffort.XHIGH && appliedEffort == DecisionEffort.XHIGH) {
                appliedEffort = DecisionEffort.HIGH;
                retryCount++;
                continue;
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("9router responses call failed with status " + response.statusCode());
            }
            return parseResponse(response.body(), requestedEffort, appliedEffort, retryCount);
        }
    }

    private JsonNode buildRequestBody(DecisionStageInputV1 input, DecisionEffort appliedEffort) {
        Map<String, Object> body = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> contentItems = java.util.List.of(
                Map.of(
                        "role", "system",
                        "content", java.util.List.of(Map.of(
                                "type", "input_text",
                                "text", "Return strict JSON only. Honor hard constraints and keep output compact."))),
                Map.of(
                        "role", "user",
                        "content", java.util.List.of(Map.of(
                                "type", "input_text",
                                "text", serialize(input)))));
        body.put("model", properties.getModel());
        body.put("parallel_tool_calls", properties.isParallelToolCalls());
        body.put("reasoning", Map.of("effort", appliedEffort.wireValue()));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "stage_output_v1",
                        "strict", properties.isStrictStructuredOutputs(),
                        "schema", outputSchema())));
        body.put("input", contentItems);
        return objectMapper.valueToTree(body);
    }

    private Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "selectedIds", Map.of("type", "array", "items", Map.of("type", "string")),
                        "assessments", Map.of("type", "object")),
                "required", java.util.List.of("selectedIds", "assessments"));
    }

    private LlmInvocationResult parseResponse(String body,
                                              DecisionEffort requestedEffort,
                                              DecisionEffort appliedEffort,
                                              int retryCount) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode usage = node.path("usage");
            Map<String, Object> tokenUsage = usage.isObject()
                    ? objectMapper.convertValue(usage, new TypeReference<>() {
                    })
                    : Map.of();
            JsonNode outputNode = firstOutputNode(node);
            Map<String, Object> parsedOutput = outputNode.isObject()
                    ? objectMapper.convertValue(outputNode, new TypeReference<>() {
                    })
                    : Map.of("raw", outputNode.asText(""));
            return new LlmInvocationResult(
                    parsedOutput,
                    requestedEffort.wireValue(),
                    appliedEffort.wireValue(),
                    tokenUsage,
                    retryCount,
                    sha256(body));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse 9router response body", exception);
        }
    }

    private JsonNode firstOutputNode(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return objectMapper.createObjectNode();
        }
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                JsonNode text = contentItem.path("text");
                if (!text.isTextual()) {
                    continue;
                }
                try {
                    return objectMapper.readTree(text.asText());
                } catch (IOException ignored) {
                    return objectMapper.createObjectNode().put("raw", text.asText());
                }
            }
        }
        return objectMapper.createObjectNode();
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize responses payload", exception);
        }
    }

    private String sha256(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record LlmInvocationResult(
            Map<String, Object> parsedOutput,
            String requestedEffort,
            String appliedEffort,
            Map<String, Object> tokenUsage,
            int retryCount,
            String rawResponseHash) {
    }

    interface ResponsesTransport {
        TransportResponse post(String baseUrl,
                               String apiKey,
                               Duration timeout,
                               JsonNode requestBody,
                               ObjectMapper objectMapper);
    }

    static final class DefaultResponsesTransport implements ResponsesTransport {
        private final HttpClient httpClient = HttpClient.newBuilder().build();

        @Override
        public TransportResponse post(String baseUrl,
                                      String apiKey,
                                      Duration timeout,
                                      JsonNode requestBody,
                                      ObjectMapper objectMapper) {
            Objects.requireNonNull(baseUrl, "baseUrl");
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(baseUrl) + "/responses"))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return new TransportResponse(response.statusCode(), response.body());
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("9router responses request interrupted", interrupted);
                }
                throw new IllegalStateException("9router responses request failed", exception);
            }
        }

        private String trimTrailingSlash(String value) {
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        }
    }

    record TransportResponse(int statusCode, String body) {
    }
}
