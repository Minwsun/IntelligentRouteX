package com.routechain.v2.harvest.path;

import com.routechain.v2.harvest.contracts.BronzeRecord;
import com.routechain.v2.harvest.contracts.DispatchOutcomeIngestRecord;

import java.nio.file.Path;

public final class HarvestPathResolver {
    private final Path root;

    public HarvestPathResolver(Path root) {
        this.root = root;
    }

    public Path bronzeFamilyPath(String family, String runId) {
        return root.resolve("bronze")
                .resolve(family)
                .resolve(sanitize(runId) + ".jsonl");
    }

    public Path silverPath(String fileName) {
        return root.resolve("silver").resolve(fileName);
    }

    public Path goldPath(String fileName) {
        return root.resolve("gold").resolve(fileName);
    }

    public Path familyPath(BronzeRecord record) {
        return bronzeFamilyPath(record.envelope().recordFamily(), record.envelope().runId());
    }

    public Path outcomeIngestPath(DispatchOutcomeIngestRecord record) {
        return root.resolve("bronze")
                .resolve("dispatch-outcome")
                .resolve(sanitize(record.traceId()) + ".jsonl");
    }

    private String sanitize(String raw) {
        return raw == null ? "unknown" : raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
