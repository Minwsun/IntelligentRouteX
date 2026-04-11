package com.routechain.core;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class WeightSnapshotStore {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "compact", "weights");

    public record StoredSnapshot(String tag, Path path, WeightSnapshot snapshot) {
    }

    public StoredSnapshot save(WeightSnapshot snapshot, String tag) {
        String safeTag = sanitizeTag(tag);
        Path path = ROOT.resolve(safeTag + ".json");
        try {
            Files.createDirectories(ROOT);
            Files.writeString(
                    path,
                    GSON.toJson(snapshot),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return new StoredSnapshot(safeTag, path, snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save compact weight snapshot " + safeTag, e);
        }
    }

    public WeightSnapshot load(String tag) {
        Path path = ROOT.resolve(sanitizeTag(tag) + ".json");
        try {
            if (!Files.exists(path)) {
                return null;
            }
            return GSON.fromJson(Files.readString(path), WeightSnapshot.class);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load compact weight snapshot " + tag, e);
        }
    }

    public StoredSnapshot saveLastGood(WeightSnapshot snapshot) {
        StoredSnapshot stored = save(snapshot, "last-good");
        save(snapshot, "latest");
        return stored;
    }

    public WeightSnapshot rollbackToLastGood() {
        WeightSnapshot snapshot = load("last-good");
        if (snapshot != null) {
            save(snapshot, "latest");
        }
        return snapshot;
    }

    public boolean hasLastGood() {
        return Files.exists(ROOT.resolve("last-good.json"));
    }

    public StoredSnapshot saveLatest(WeightSnapshot snapshot, String tag) {
        StoredSnapshot stored = save(snapshot, tag);
        save(snapshot, "latest");
        return stored;
    }

    private String sanitizeTag(String tag) {
        return tag == null || tag.isBlank()
                ? "snapshot"
                : tag.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
