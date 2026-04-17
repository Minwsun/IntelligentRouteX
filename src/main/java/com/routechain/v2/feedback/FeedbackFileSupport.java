package com.routechain.v2.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class FeedbackFileSupport {
    private final Path directory;
    private final int maxFiles;
    private final ObjectMapper objectMapper;

    FeedbackFileSupport(Path directory, int maxFiles) {
        this.directory = directory;
        this.maxFiles = maxFiles;
        this.objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    <T> void writeJson(String fileName, T value) {
        try {
            Files.createDirectories(directory);
            objectMapper.writeValue(directory.resolve(fileName).toFile(), value);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write feedback file " + fileName, exception);
        }
    }

    <T> T readJson(String fileName, Class<T> type) {
        try {
            Path file = directory.resolve(fileName);
            if (!Files.exists(file)) {
                return null;
            }
            return objectMapper.readValue(file.toFile(), type);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read feedback file " + fileName, exception);
        }
    }

    void writePointer(String pointerFileName, String value) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(pointerFileName), value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write pointer " + pointerFileName, exception);
        }
    }

    String readPointer(String pointerFileName) {
        try {
            Path file = directory.resolve(pointerFileName);
            if (!Files.exists(file)) {
                return null;
            }
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read pointer " + pointerFileName, exception);
        }
    }

    Path directory() {
        return directory;
    }

    void enforceRetention(String pointerFileName) {
        if (maxFiles <= 0) {
            return;
        }
        try (Stream<Path> stream = Files.exists(directory) ? Files.list(directory) : Stream.empty()) {
            List<Path> dataFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed())
                    .toList();
            for (int index = maxFiles; index < dataFiles.size(); index++) {
                Files.deleteIfExists(dataFiles.get(index));
            }
            String currentPointer = readPointer(pointerFileName);
            if (currentPointer != null && !Files.exists(directory.resolve(currentPointer))) {
                if (dataFiles.isEmpty()) {
                    Files.deleteIfExists(directory.resolve(pointerFileName));
                } else {
                    writePointer(pointerFileName, dataFiles.getFirst().getFileName().toString());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to enforce feedback retention in " + directory, exception);
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to get last modified time for " + path, exception);
        }
    }

    static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
