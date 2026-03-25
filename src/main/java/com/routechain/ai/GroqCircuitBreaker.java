package com.routechain.ai;

import java.time.Duration;
import java.time.Instant;

/**
 * Small provider-level circuit breaker for repeated Groq transport/schema failures.
 */
public final class GroqCircuitBreaker {
    private final int failureThreshold;
    private final Duration openDuration;
    private int consecutiveFailures = 0;
    private Instant openUntil = Instant.EPOCH;

    public GroqCircuitBreaker() {
        this(3, Duration.ofMinutes(5));
    }

    public GroqCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null ? Duration.ofMinutes(5) : openDuration;
    }

    public synchronized boolean isOpen(Instant now) {
        return now.isBefore(openUntil);
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openUntil = Instant.EPOCH;
    }

    public synchronized void recordFailure(Instant now) {
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openUntil = now.plus(openDuration);
            consecutiveFailures = 0;
        }
    }
}
