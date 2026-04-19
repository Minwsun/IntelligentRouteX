package com.routechain.simulator.runtime;

import java.time.Instant;
import java.util.List;

public record DecisionPoint(
        String traceId,
        int tickIndex,
        Instant decisionTime,
        List<String> openOrderIds,
        List<String> availableDriverIds) {
}
