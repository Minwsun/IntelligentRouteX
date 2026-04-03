package com.routechain.backend.offer;

import java.time.Instant;

/**
 * Driver app operational session state.
 */
public record DriverSessionState(
        String driverId,
        String deviceId,
        boolean available,
        double lastLat,
        double lastLng,
        Instant lastSeenAt,
        String activeOfferId
) {
    public DriverSessionState {
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        deviceId = deviceId == null || deviceId.isBlank() ? "device-unknown" : deviceId;
        lastSeenAt = lastSeenAt == null ? Instant.now() : lastSeenAt;
        activeOfferId = activeOfferId == null ? "" : activeOfferId;
    }

    public DriverSessionState withHeartbeat() {
        return new DriverSessionState(driverId, deviceId, available, lastLat, lastLng, Instant.now(), activeOfferId);
    }

    public DriverSessionState withAvailability(boolean nextAvailable) {
        return new DriverSessionState(driverId, deviceId, nextAvailable, lastLat, lastLng, Instant.now(), activeOfferId);
    }

    public DriverSessionState withLocation(double lat, double lng) {
        return new DriverSessionState(driverId, deviceId, available, lat, lng, Instant.now(), activeOfferId);
    }

    public DriverSessionState withActiveOffer(String offerId) {
        return new DriverSessionState(driverId, deviceId, available, lastLat, lastLng, Instant.now(), offerId);
    }
}
