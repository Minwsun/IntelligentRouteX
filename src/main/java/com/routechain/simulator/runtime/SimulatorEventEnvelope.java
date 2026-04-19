package com.routechain.simulator.runtime;

import java.time.Instant;

public record SimulatorEventEnvelope(
        String runId,
        long sequenceNumber,
        String family,
        Instant emittedAt,
        Object payload) {
}
