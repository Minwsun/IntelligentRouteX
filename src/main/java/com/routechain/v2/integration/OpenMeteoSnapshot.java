package com.routechain.v2.integration;

public record OpenMeteoSnapshot(
        boolean available,
        long sourceAgeMs,
        double confidence) {

    public static OpenMeteoSnapshot unavailable() {
        return new OpenMeteoSnapshot(false, Long.MAX_VALUE, 0.0);
    }
}

