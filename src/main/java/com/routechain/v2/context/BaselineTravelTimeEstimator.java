package com.routechain.v2.context;

import com.routechain.domain.GeoPoint;

public final class BaselineTravelTimeEstimator {
    public double estimateMinutes(GeoPoint from, GeoPoint to, double baselineSpeedKph) {
        if (from == null || to == null) {
            return 0.0;
        }
        double distanceKm = distanceKm(from, to);
        double speed = Math.max(1.0, baselineSpeedKph);
        return (distanceKm / speed) * 60.0;
    }

    public double distanceKm(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            return 0.0;
        }
        double earthRadiusKm = 6371.0;
        double latDistance = Math.toRadians(to.latitude() - from.latitude());
        double lonDistance = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(from.latitude()))
                * Math.cos(Math.toRadians(to.latitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}

