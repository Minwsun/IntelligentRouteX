package com.routechain.ai;

import java.time.Instant;

/**
 * Canonical model-inference trace for online scoring observability.
 */
public record ModelInferenceEventV1(
        String runId,
        String traceId,
        String driverId,
        String etaModelVersion,
        String planRankerModelVersion,
        double executionScore,
        double futureScore,
        double totalScore,
        boolean executionGatePassed,
        Instant inferredAt
) {}
