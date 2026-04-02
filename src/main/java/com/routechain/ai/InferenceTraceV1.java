package com.routechain.ai;

import java.time.Instant;

/**
 * Canonical online model inference trace for champion/challenger observability.
 */
public record InferenceTraceV1(
        String runId,
        String traceId,
        String modelKey,
        String modelVersion,
        String modelFamily,
        String backend,
        long latencyMs,
        double score,
        boolean hotPathUsed,
        boolean cacheHit,
        boolean timeoutFallback,
        boolean fallbackUsed,
        String fallbackReason,
        Instant inferredAt
) {}
