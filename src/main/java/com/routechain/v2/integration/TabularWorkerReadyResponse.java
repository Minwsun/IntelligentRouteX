package com.routechain.v2.integration;

public record TabularWorkerReadyResponse(
        String schemaVersion,
        boolean ready,
        String reason) {
}
