package com.routechain.v2.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.config.RouteChainDispatchV2Properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DecisionStageLogger {
    private final boolean enabled;
    private final Path baseDirectory;
    private final ObjectMapper objectMapper;

    public DecisionStageLogger(RouteChainDispatchV2Properties properties) {
        this.enabled = properties.getFeedback().isDecisionLogEnabled();
        this.baseDirectory = Path.of(properties.getFeedback().getBaseDir()).resolve("decision-stage");
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    public void writeFamily(String family, String traceId, String stageKey, Object payload) {
        if (!enabled || traceId == null || traceId.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(baseDirectory.resolve(sanitize(family)));
            String suffix = stageKey == null || stageKey.isBlank() ? "" : "-" + sanitize(stageKey);
            Path file = baseDirectory.resolve(sanitize(family))
                    .resolve(sanitize(traceId) + suffix + ".json");
            objectMapper.writeValue(file.toFile(), payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write decision trace family " + family, exception);
        }
    }

    private String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
