package com.routechain.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Small abstraction to make Groq HTTP calls testable without network access.
 */
public interface GroqTransport {
    GroqTransportResult chatCompletion(String url,
                                       String apiKey,
                                       String modelId,
                                       String systemPrompt,
                                       String userPrompt,
                                       int maxOutputTokens,
                                       int timeoutMs) throws IOException, InterruptedException;

    record GroqTransportResult(
            int statusCode,
            String body,
            Map<String, List<String>> headers,
            long latencyMs
    ) {}
}
