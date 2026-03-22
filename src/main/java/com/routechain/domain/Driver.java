package com.routechain.domain;

import com.routechain.domain.Enums.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Driver entity — represents a courier on the fleet.
 */
public class Driver {
    private final String id;
    private final String name;
    private volatile GeoPoint currentLocation;
    private volatile DriverState state;
    private final String regionId;
    private final VehicleType vehicleType;
    private volatile String activeBundleId;
    private volatile double utilizationScore;
    private volatile double netEarningToday;
    private volatile double acceptanceRate;
    private volatile Instant lastSeenAt;
    private final List<String> activeOrderIds = new ArrayList<>();
    private volatile GeoPoint targetLocation;
    private volatile double speedKmh;
    private volatile double heading;
    private final List<GeoPoint> routeWaypoints = new ArrayList<>();
    private volatile int currentWaypointIndex = 0;

    public Driver(String id, String name, GeoPoint initialLocation,
                  String regionId, VehicleType vehicleType) {
        this.id = id;
        this.name = name;
        this.currentLocation = initialLocation;
        this.state = DriverState.ONLINE_IDLE;
        this.regionId = regionId;
        this.vehicleType = vehicleType;
        this.utilizationScore = 0.0;
        this.netEarningToday = 0.0;
        this.acceptanceRate = 0.85 + Math.random() * 0.15;
        this.lastSeenAt = Instant.now();
        this.speedKmh = 0;
        this.heading = 0;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public GeoPoint getCurrentLocation() { return currentLocation; }
    public DriverState getState() { return state; }
    public String getRegionId() { return regionId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getActiveBundleId() { return activeBundleId; }
    public double getUtilizationScore() { return utilizationScore; }
    public double getNetEarningToday() { return netEarningToday; }
    public double getAcceptanceRate() { return acceptanceRate; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public List<String> getActiveOrderIds() { return List.copyOf(activeOrderIds); }
    public GeoPoint getTargetLocation() { return targetLocation; }
    public double getSpeedKmh() { return speedKmh; }
    public double getHeading() { return heading; }

    // State transitions
    public void setState(DriverState state) { this.state = state; }
    public void setCurrentLocation(GeoPoint location) {
        this.currentLocation = location;
        this.lastSeenAt = Instant.now();
    }
    public void setTargetLocation(GeoPoint target) { this.targetLocation = target; }
    public void setSpeedKmh(double speed) { this.speedKmh = speed; }
    public void setHeading(double heading) { this.heading = heading; }
    public void addOrder(String orderId) { activeOrderIds.add(orderId); }
    public void removeOrder(String orderId) { activeOrderIds.remove(orderId); }
    public void addEarning(double amount) { this.netEarningToday += amount; }
    public void setUtilizationScore(double score) { this.utilizationScore = score; }
    public void setActiveBundleId(String bundleId) { this.activeBundleId = bundleId; }

    /**
     * Set route waypoints from OSRM. Driver follows these sequentially.
     * Coordinates are [lng, lat] pairs — converted to GeoPoint.
     */
    public void setRouteWaypoints(List<double[]> coordinates) {
        synchronized (routeWaypoints) {
            routeWaypoints.clear();
            if (coordinates != null) {
                for (double[] c : coordinates) {
                    routeWaypoints.add(new GeoPoint(c[1], c[0])); // OSRM: [lng, lat]
                }
            }
            currentWaypointIndex = 0;
        }
    }

    /**
     * Get the current waypoint to move toward.
     * Returns null if no waypoints or all consumed.
     */
    public GeoPoint getCurrentWaypoint() {
        synchronized (routeWaypoints) {
            if (currentWaypointIndex < routeWaypoints.size()) {
                return routeWaypoints.get(currentWaypointIndex);
            }
        }
        return null;
    }

    /**
     * Advance to the next waypoint. Called when driver arrives near current waypoint.
     */
    public void advanceWaypoint() {
        synchronized (routeWaypoints) {
            if (currentWaypointIndex < routeWaypoints.size()) {
                currentWaypointIndex++;
            }
        }
    }

    /**
     * Check if driver has route waypoints to follow.
     */
    public boolean hasRouteWaypoints() {
        synchronized (routeWaypoints) {
            return currentWaypointIndex < routeWaypoints.size();
        }
    }

    public void clearRouteWaypoints() {
        synchronized (routeWaypoints) {
            routeWaypoints.clear();
            currentWaypointIndex = 0;
        }
    }

    public boolean isAvailable() {
        return state == DriverState.ONLINE_IDLE && activeOrderIds.isEmpty();
    }
}
