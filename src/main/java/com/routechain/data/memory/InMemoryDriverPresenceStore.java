package com.routechain.data.memory;

import com.routechain.data.port.DriverPresenceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "routechain.persistence.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDriverPresenceStore implements DriverPresenceStore {
    private final Map<String, PresenceSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public void heartbeat(String driverId, double lat, double lng, boolean available, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl == null || ttl.isNegative() ? Duration.ofMinutes(2) : ttl);
        snapshots.put(driverId, new PresenceSnapshot(driverId, lat, lng, available, expiresAt));
    }

    @Override
    public Optional<PresenceSnapshot> find(String driverId) {
        PresenceSnapshot snapshot = snapshots.get(driverId);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (snapshot.expiresAt().isBefore(Instant.now())) {
            snapshots.remove(driverId);
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }
}
