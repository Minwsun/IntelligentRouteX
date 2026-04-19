package com.routechain.simulator.traffic;

import java.time.Instant;
import java.util.Map;

public record TrafficSnapshot(
        Instant observedAt,
        double speedMultiplier,
        double congestionLevel,
        String mode,
        Map<String, Double> corridorMultiplierById) {
}
