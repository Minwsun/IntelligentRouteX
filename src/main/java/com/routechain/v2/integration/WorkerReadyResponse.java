package com.routechain.v2.integration;

public record WorkerReadyResponse(
        String schemaVersion,
        boolean ready,
        String reason) {
}
