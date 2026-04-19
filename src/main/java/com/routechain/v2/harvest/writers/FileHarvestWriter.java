package com.routechain.v2.harvest.writers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.v2.harvest.contracts.BronzeRecord;
import com.routechain.v2.harvest.contracts.DispatchOutcomeIngestRecord;
import com.routechain.v2.harvest.path.HarvestPathResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileHarvestWriter implements HarvestWriter {
    private final HarvestPathResolver pathResolver;
    private final ObjectMapper objectMapper;

    public FileHarvestWriter(Path root) {
        this.pathResolver = new HarvestPathResolver(root);
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    @Override
    public void write(BronzeRecord record) {
        append(pathResolver.familyPath(record), toJson(record));
    }

    @Override
    public void writeOutcome(DispatchOutcomeIngestRecord record) {
        append(pathResolver.outcomeIngestPath(record), toJson(record));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize harvest record", exception);
        }
    }

    private void append(Path path, String line) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append harvest record to " + path, exception);
        }
    }
}
