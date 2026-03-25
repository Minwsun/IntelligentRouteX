package com.routechain.infra;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Local-first canonical event tape persisted as JSONL.
 */
public final class JsonlCanonicalEventPublisher implements CanonicalEventPublisher {
    private static final com.google.gson.Gson GSON = GsonSupport.compact();

    private final Path eventTapeFile;

    public JsonlCanonicalEventPublisher(Path rootDir) {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create canonical event directory", e);
        }
        this.eventTapeFile = rootDir.resolve("canonical-events.jsonl");
    }

    @Override
    public synchronized void publish(String topic, Object payload) {
        CanonicalEnvelope envelope = new CanonicalEnvelope(
                topic,
                payload.getClass().getSimpleName(),
                Instant.now(),
                payload
        );
        try {
            Files.writeString(
                    eventTapeFile,
                    GSON.toJson(envelope) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to persist canonical event " + topic, e);
        }
    }

    private record CanonicalEnvelope(
            String topic,
            String payloadType,
            Instant publishedAt,
            Object payload
    ) {}
}
