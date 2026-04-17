package com.routechain.v2.integration;

public record RouteFinderRequestEnvelope(
        String schemaVersion,
        String traceId,
        String stageName,
        long timeoutBudgetMs,
        String modelContractVersion,
        Object payload) {
}
