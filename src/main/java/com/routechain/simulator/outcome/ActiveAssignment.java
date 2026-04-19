package com.routechain.simulator.outcome;

import java.time.Instant;
import java.util.List;

public record ActiveAssignment(
        String assignmentId,
        String traceId,
        String driverId,
        List<String> orderIds,
        Instant assignedAt,
        Instant completesAt,
        long pickupTravelSeconds,
        long merchantWaitSeconds,
        long dropoffTravelSeconds,
        long trafficDelaySeconds,
        String weatherModifier) {
}
