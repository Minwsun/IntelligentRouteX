package com.routechain.simulator.logging;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BronzeArtifactWriter {
    private final Path runDir;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Map<String, String> artifactFiles = new LinkedHashMap<>();

    public BronzeArtifactWriter(Path runDir, ObjectMapper objectMapper, boolean enabled) {
        this.runDir = runDir;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void append(String family, Object payload) {
        if (!enabled && !"run_manifest".equals(family)) {
            return;
        }
        try {
            Files.createDirectories(runDir);
            Path file = runDir.resolve(family + ".jsonl");
            artifactFiles.putIfAbsent(family, file.toString());
            String json = objectMapper.writeValueAsString(payload) + System.lineSeparator();
            Files.writeString(file, json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write simulator artifact family " + family, exception);
        }
    }

    public BronzeArtifactManifest manifest(String runId, String sliceId) {
        return new BronzeArtifactManifest(
                "bronze-artifact-manifest/v1",
                runId,
                sliceId,
                Map.copyOf(artifactFiles));
    }
}
