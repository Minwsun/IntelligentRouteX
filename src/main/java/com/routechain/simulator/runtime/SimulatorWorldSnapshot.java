package com.routechain.simulator.runtime;

import java.time.Instant;
import java.util.List;

public record SimulatorWorldSnapshot(
        String runId,
        String sliceId,
        int worldIndex,
        long sequenceNumber,
        Instant worldTime,
        int tickIndex,
        int openOrderCount,
        int assignedOrderCount,
        int deliveredOrderCount,
        int availableDriverCount,
        List<SimulatorLayerPayload> layers) {
}
