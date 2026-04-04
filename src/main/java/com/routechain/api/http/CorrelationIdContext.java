package com.routechain.api.http;

import java.util.Optional;

public final class CorrelationIdContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIdContext() {
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
    }

    public static Optional<String> currentId() {
        return Optional.ofNullable(CURRENT.get()).filter(value -> !value.isBlank());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
