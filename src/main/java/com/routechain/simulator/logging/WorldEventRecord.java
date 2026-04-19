package com.routechain.simulator.logging;

import java.time.Instant;
import java.util.Map;

public record WorldEventRecord(
        String schemaVersion,
        String runId,
        String sliceId,
        int worldIndex,
        int tickIndex,
        String family,
        Instant worldTime,
        Map<String, Object> payload) {
}
