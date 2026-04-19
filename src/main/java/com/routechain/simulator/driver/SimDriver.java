package com.routechain.simulator.driver;

import com.routechain.domain.GeoPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SimDriver {
    private final String driverId;
    private GeoPoint currentLocation;
    private SimDriverStatus status;
    private Instant availableAt;
    private final List<String> activeOrderIds;

    public SimDriver(String driverId, GeoPoint currentLocation, SimDriverStatus status, Instant availableAt, List<String> activeOrderIds) {
        this.driverId = driverId;
        this.currentLocation = currentLocation;
        this.status = status;
        this.availableAt = availableAt;
        this.activeOrderIds = new ArrayList<>(activeOrderIds);
    }

    public String driverId() {
        return driverId;
    }

    public GeoPoint currentLocation() {
        return currentLocation;
    }

    public void currentLocation(GeoPoint currentLocation) {
        this.currentLocation = currentLocation;
    }

    public SimDriverStatus status() {
        return status;
    }

    public void status(SimDriverStatus status) {
        this.status = status;
    }

    public Instant availableAt() {
        return availableAt;
    }

    public void availableAt(Instant availableAt) {
        this.availableAt = availableAt;
    }

    public List<String> activeOrderIds() {
        return List.copyOf(activeOrderIds);
    }

    public void replaceActiveOrderIds(List<String> orderIds) {
        activeOrderIds.clear();
        activeOrderIds.addAll(orderIds);
    }
}
