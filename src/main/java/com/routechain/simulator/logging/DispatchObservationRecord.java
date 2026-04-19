package com.routechain.simulator.logging;

import com.routechain.v2.DispatchV2Request;

import java.time.Instant;
import java.util.List;

public record DispatchObservationRecord(
        String schemaVersion,
        String runId,
        String sliceId,
        int worldIndex,
        int tickIndex,
        String traceId,
        Instant worldTime,
        long seed,
        List<String> openOrderIds,
        List<String> availableDriverIds,
        DispatchV2Request request) {
}
