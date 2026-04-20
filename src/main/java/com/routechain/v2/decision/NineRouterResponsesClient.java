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
import java.util.ArrayList;
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
            throw new IllegalStateException("llm-api-key-missing");
        }
        String baseUrl = configuredValue(
                "routechain.dispatch-v2.decision.llm.base-url",
                "ROUTECHAIN_DECISION_LLM_BASE_URL",
                properties.getBaseUrl());
        String model = configuredValue(
                "routechain.dispatch-v2.decision.llm.model",
                "ROUTECHAIN_DECISION_LLM_MODEL",
                properties.getModel());
        DecisionEffort appliedEffort = requestedEffort == null ? DecisionEffort.MEDIUM : requestedEffort;
        int retryCount = 0;
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        String lastFailureReason = "provider-empty-response";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            JsonNode requestBody = buildRequestBody(input, appliedEffort, model);
            try {
                TransportResponse response = transport.post(
                        baseUrl,
                        apiKey,
                        properties.getTimeoutMs(),
                        requestBody,
                        objectMapper);
                if (response.statusCode() >= 400) {
                    String failureReason = classifyHttpFailure(response.statusCode(), response.body());
                    if ("provider-rejected-effort".equals(failureReason)
                            && requestedEffort == DecisionEffort.XHIGH
                            && appliedEffort == DecisionEffort.XHIGH) {
                        appliedEffort = DecisionEffort.HIGH;
                        retryCount++;
                        lastFailureReason = failureReason;
                        continue;
                    }
                    if (attempt >= maxAttempts) {
                        throw new IllegalStateException(failureReason);
                    }
                    retryCount++;
                    lastFailureReason = failureReason;
                    continue;
                }
                return parseResponse(response.body(), requestedEffort, appliedEffort, retryCount, model);
            } catch (IllegalStateException exception) {
                String failureReason = exception.getMessage() == null ? "provider-http-error" : exception.getMessage();
                if (attempt >= maxAttempts || !retryable(failureReason)) {
                    throw new IllegalStateException(failureReason, exception);
                }
                retryCount++;
                lastFailureReason = failureReason;
            }
        }
        throw new IllegalStateException(lastFailureReason);
    }

    private JsonNode buildRequestBody(DecisionStageInputV1 input, DecisionEffort appliedEffort, String model) {
        Map<String, Object> body = new LinkedHashMap<>();
        java.util.List<Map<String, Object>> contentItems = java.util.List.of(
                Map.of(
                        "role", "system",
                        "content", java.util.List.of(Map.of(
                                "type", "input_text",
                                "text", systemPrompt(input)))),
                Map.of(
                        "role", "user",
                        "content", java.util.List.of(Map.of(
                                "type", "input_text",
                                "text", dynamicPrompt(input)))));
        body.put("model", model);
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
                        "assessments", Map.of("type", "object", "additionalProperties", true)),
                "required", java.util.List.of("selectedIds", "assessments"));
    }

    private LlmInvocationResult parseResponse(String body,
                                              DecisionEffort requestedEffort,
                                              DecisionEffort appliedEffort,
                                              int retryCount,
                                              String model) {
        try {
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("provider-empty-response");
            }
            JsonNode node = objectMapper.readTree(body);
            JsonNode usage = node.path("usage");
            Map<String, Object> tokenUsage = usage.isObject()
                    ? objectMapper.convertValue(usage, new TypeReference<>() {
                    })
                    : Map.of();
            JsonNode outputNode = firstOutputNode(node);
            Map<String, Object> parsedOutput = validateParsedOutput(outputNode);
            tokenUsage = normalizeTokenUsage(tokenUsage);
            return new LlmInvocationResult(
                    parsedOutput,
                    requestedEffort.wireValue(),
                    appliedEffort.wireValue(),
                    tokenUsage,
                    retryCount,
                    sha256(body),
                    model);
        } catch (IOException exception) {
            throw new IllegalStateException("provider-invalid-json", exception);
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
                    throw new IllegalStateException("provider-invalid-json");
                }
            }
        }
        throw new IllegalStateException("provider-empty-response");
    }

    private Map<String, Object> validateParsedOutput(JsonNode outputNode) {
        if (!outputNode.isObject()) {
            throw new IllegalStateException("provider-schema-invalid");
        }
        JsonNode selectedIds = outputNode.get("selectedIds");
        JsonNode assessments = outputNode.get("assessments");
        if (selectedIds == null || !selectedIds.isArray() || assessments == null || !assessments.isObject()) {
            throw new IllegalStateException("provider-schema-invalid");
        }
        return objectMapper.convertValue(outputNode, new TypeReference<>() {
        });
    }

    private Map<String, Object> normalizeTokenUsage(Map<String, Object> tokenUsage) {
        if (tokenUsage == null || tokenUsage.isEmpty()) {
            return Map.of();
        }
        long inputTokens = longValue(tokenUsage, "input_tokens", "inputTokens", "prompt_tokens", "promptTokens");
        long outputTokens = longValue(tokenUsage, "output_tokens", "outputTokens", "completion_tokens", "completionTokens");
        long totalTokens = longValue(tokenUsage, "total_tokens", "totalTokens");
        if (totalTokens <= 0) {
            totalTokens = inputTokens + outputTokens;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("inputTokens", inputTokens);
        normalized.put("outputTokens", outputTokens);
        normalized.put("totalTokens", totalTokens);
        return Map.copyOf(normalized);
    }

    private long longValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
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

    private String systemPrompt(DecisionStageInputV1 input) {
        return String.join(
                "\n",
                "Return strict JSON only.",
                "Honor hard constraints and keep output compact.",
                "Do not invent ids that do not exist in the candidate window.",
                "Static prefix: " + String.valueOf(input.constraints().getOrDefault("staticPrefix", "")),
                "Schema: stage_output_v1 with selectedIds[] and assessments{}.");
    }

    private String dynamicPrompt(DecisionStageInputV1 input) {
        java.util.List<String> sections = new ArrayList<>();
        sections.add("dispatchContext=" + serialize(input.dispatchContext()));
        sections.add("candidateSet=" + serialize(input.candidateSet()));
        sections.add("constraints=" + serialize(input.constraints()));
        sections.add("objectiveWeights=" + serialize(input.objectiveWeights()));
        sections.add("upstreamRefs=" + serialize(input.upstreamRefs()));
        return String.join("\n", sections);
    }

    private String classifyHttpFailure(int statusCode, String body) {
        String normalizedBody = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
        if ((statusCode == 400 || statusCode == 422)
                && normalizedBody.contains("effort")) {
            return "provider-rejected-effort";
        }
        if (statusCode == 408 || statusCode == 429) {
            return "provider-timeout";
        }
        if (statusCode >= 500) {
            return "provider-http-error";
        }
        return "provider-http-error";
    }

    private boolean retryable(String failureReason) {
        return "provider-timeout".equals(failureReason)
                || "provider-http-error".equals(failureReason)
                || "provider-empty-response".equals(failureReason)
                || "provider-invalid-json".equals(failureReason)
                || "provider-rejected-effort".equals(failureReason);
    }

    private String configuredValue(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }

    public record LlmInvocationResult(
            Map<String, Object> parsedOutput,
            String requestedEffort,
            String appliedEffort,
            Map<String, Object> tokenUsage,
            int retryCount,
            String rawResponseHash,
            String providerModel) {
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
