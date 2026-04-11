package com.routechain.simulation;

import java.util.List;
import java.util.Locale;
import java.util.stream.LongStream;

public enum CompactBenchmarkLane {
    SMOKE("compactSmoke", 3),
    CERTIFICATION("compactCertification", 20),
    NIGHTLY("compactNightly", 60);

    private final String taskName;
    private final int seedCount;

    CompactBenchmarkLane(String taskName, int seedCount) {
        this.taskName = taskName;
        this.seedCount = seedCount;
    }

    public String taskName() {
        return taskName;
    }

    public int seedCount() {
        return seedCount;
    }

    public List<Long> seeds() {
        long baseSeed = 2026041101L;
        return LongStream.range(0, seedCount)
                .map(index -> baseSeed + index)
                .boxed()
                .toList();
    }

    public static CompactBenchmarkLane fromArg(String raw) {
        if (raw == null || raw.isBlank()) {
            return SMOKE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "smoke", "compactsmoke" -> SMOKE;
            case "cert", "certification", "compactcertification" -> CERTIFICATION;
            case "nightly", "compactnightly" -> NIGHTLY;
            default -> SMOKE;
        };
    }
}
