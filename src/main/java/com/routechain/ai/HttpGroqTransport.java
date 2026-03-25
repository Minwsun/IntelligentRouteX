package com.routechain.ai;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST transport for Groq's OpenAI-compatible chat completions endpoint.
 */
public final class HttpGroqTransport implements GroqTransport {
    private static final Gson GSON = GsonSupport.compact();

    private final HttpClient httpClient;

    public HttpGroqTransport() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    HttpGroqTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GroqTransportResult chatCompletion(String url,
                                              String apiKey,
                                              String modelId,
                                              String systemPrompt,
                                              String userPrompt,
                                              int maxOutputTokens,
                                              int timeoutMs) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("temperature", 0.1);
        body.put("max_tokens", maxOutputTokens);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        long startedAt = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return new GroqTransportResult(
                response.statusCode(),
                response.body(),
                response.headers().map(),
                latencyMs
        );
    }
}
