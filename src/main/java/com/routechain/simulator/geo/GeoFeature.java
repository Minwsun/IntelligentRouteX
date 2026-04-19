package com.routechain.simulator.geo;

import com.routechain.domain.GeoPoint;

import java.util.Map;

public record GeoFeature(
        String featureId,
        String featureType,
        GeoPoint point,
        Map<String, Object> properties) {
}
