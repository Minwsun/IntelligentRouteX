package com.routechain.v2.integration;

public record ForecastRequestEnvelope(
        String schemaVersion,
        String traceId,
        String stageName,
        long timeoutBudgetMs,
        String contractVersion,
        Object payload) {
}
