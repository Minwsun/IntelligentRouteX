package com.routechain.data.port;

import com.routechain.backend.offer.DriverSessionState;
import com.routechain.domain.GeoPoint;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface DriverFleetRepository {
    void saveDriverSession(DriverSessionState sessionState);
    Optional<DriverSessionState> findDriverSession(String driverId);
    Collection<DriverSessionState> allDriverSessions();
    void recordDriverLocation(String driverId, GeoPoint location, Instant recordedAt);
}
