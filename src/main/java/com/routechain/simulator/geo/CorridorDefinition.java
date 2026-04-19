package com.routechain.simulator.geo;

import com.routechain.domain.GeoPoint;

import java.util.List;
import java.util.Map;

public record CorridorDefinition(
        String corridorId,
        String className,
        List<GeoPoint> path,
        double baseSpeedKph,
        Map<String, Object> properties) {
}
