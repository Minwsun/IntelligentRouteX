package com.routechain.simulator.logging;

import java.time.Instant;
import java.util.Map;

public record TeacherTraceRecord(
        String schemaVersion,
        String runId,
        String sliceId,
        int worldIndex,
        int tickIndex,
        String traceId,
        Instant worldTime,
        long seed,
        String teacherFamily,
        String stageName,
        Map<String, Object> payload) {
}
