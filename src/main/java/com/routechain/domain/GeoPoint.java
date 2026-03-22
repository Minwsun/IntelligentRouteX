package com.routechain.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Core domain models for RouteChain AI.
 * Immutable records representing the bounded contexts.
 */

// ── Geographic ──────────────────────────────────────────────────────────
public record GeoPoint(double lat, double lng) {
    public double distanceTo(GeoPoint other) {
        double R = 6371_000; // meters
        double dLat = Math.toRadians(other.lat - this.lat);
        double dLng = Math.toRadians(other.lng - this.lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(this.lat)) * Math.cos(Math.toRadians(other.lat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public GeoPoint moveTowards(GeoPoint target, double distanceMeters) {
        double totalDist = distanceTo(target);
        if (totalDist < 1.0) return target;
        double fraction = Math.min(distanceMeters / totalDist, 1.0);
        return new GeoPoint(
                lat + (target.lat - lat) * fraction,
                lng + (target.lng - lng) * fraction
        );
    }
}
