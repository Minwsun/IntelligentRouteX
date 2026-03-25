package com.routechain.infra;

/**
 * Durable publisher for canonical event topics.
 */
public interface CanonicalEventPublisher {
    void publish(String topic, Object payload);
}
