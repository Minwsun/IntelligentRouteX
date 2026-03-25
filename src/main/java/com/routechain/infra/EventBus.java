package com.routechain.infra;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight typed event bus for decoupled component communication.
 * Thread-safe for JavaFX multi-threaded simulation.
 */
public final class EventBus {
    private static final EventBus INSTANCE = new EventBus();
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();
    private final List<Consumer<Object>> globalListeners = new CopyOnWriteArrayList<>();

    private EventBus() {}

    public static EventBus getInstance() { return INSTANCE; }

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    public void subscribeAll(Consumer<Object> handler) {
        globalListeners.add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        for (Consumer<Object> handler : globalListeners) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                System.err.println("[EventBus] Global handler error for " + event.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        List<Consumer<?>> handlers = listeners.get(event.getClass());
        if (handlers != null) {
            for (Consumer<?> handler : handlers) {
                try {
                    ((Consumer<T>) handler).accept(event);
                } catch (Exception e) {
                    System.err.println("[EventBus] Handler error for " + event.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
    }

    public void clear() {
        listeners.clear();
        globalListeners.clear();
    }
}
