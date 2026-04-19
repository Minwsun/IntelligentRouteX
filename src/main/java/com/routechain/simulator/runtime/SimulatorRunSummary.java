package com.routechain.simulator.runtime;

import java.time.Instant;

public record SimulatorRunSummary(
        String runId,
        SimulatorRunStatus status,
        SimulatorRunConfig config,
        String sliceId,
        int totalWorlds,
        int completedWorlds,
        long sequenceNumber,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String statusMessage) {
}
