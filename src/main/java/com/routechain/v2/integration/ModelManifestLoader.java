package com.routechain.v2.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class ModelManifestLoader {
    private final ObjectMapper objectMapper;

    public ModelManifestLoader() {
        this.objectMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    }

    public TabularWorkerManifest load(Path manifestPath) {
        try {
            return objectMapper.readValue(manifestPath.toFile(), TabularWorkerManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load model manifest from " + manifestPath, exception);
        }
    }
}
