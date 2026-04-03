package com.routechain.data.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface DriverPresenceStore {
    void heartbeat(String driverId, double lat, double lng, boolean available, Duration ttl);
    Optional<PresenceSnapshot> find(String driverId);

    record PresenceSnapshot(
            String driverId,
            double lat,
            double lng,
            boolean available,
            Instant expiresAt
    ) {}
}
