package com.routechain.domain;

import com.routechain.domain.Enums.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Driver entity — represents a courier on the fleet.
 * Full productivity and economics tracking for KPI computation.
 */
public class Driver {
    // ── Identity ────────────────────────────────────────────────────────
    private final String id;
    private final String name;
    private final String regionId;
    private final VehicleType vehicleType;

    // ── Runtime state ───────────────────────────────────────────────────
    private volatile GeoPoint currentLocation;
    private volatile DriverState state;
    private volatile String activeBundleId;
    private volatile GeoPoint targetLocation;
    private volatile double speedKmh;
    private volatile double heading;
    private volatile Instant lastSeenAt;
    private final List<String> activeOrderIds = new ArrayList<>();

    // ── Route waypoints ─────────────────────────────────────────────────
    private final List<GeoPoint> routeWaypoints = new ArrayList<>();
    private volatile int currentWaypointIndex = 0;

    // ── Productivity accumulators ────────────────────────────────────────
    private volatile long onlineTicks = 0;
    private volatile long busyTicks = 0;
    private volatile long idleTicks = 0;
    private volatile double deadheadDistance = 0.0;    // km
    private volatile double deadheadTime = 0.0;        // minutes
    private volatile int completedOrders = 0;
    private volatile int bundlesCompleted = 0;

    // ── Economics ────────────────────────────────────────────────────────
    private volatile double grossEarning = 0.0;
    private volatile double netEarningToday = 0.0;
    private volatile double fuelCostEstimate = 0.0;
    private volatile double avgEarningPerHour = 0.0;

    // ── Quality / behavior ──────────────────────────────────────────────
    private volatile double acceptanceRate;
    private volatile double cancellationRate = 0.0;
    private volatile double reliabilityScore = 1.0;
    private volatile double utilizationScore = 0.0;

    // ── Prediction / dispatch ───────────────────────────────────────────
    private volatile double continuationValueCurrentZone = 0.0;
    private volatile double shortageContribution = 0.0;
    private volatile double overloadRisk = 0.0;
    private volatile double predictedAcceptanceProb = 0.85;

    public Driver(String id, String name, GeoPoint initialLocation,
                  String regionId, VehicleType vehicleType) {
        this.id = id;
        this.name = name;
        this.currentLocation = initialLocation;
        this.state = DriverState.ONLINE_IDLE;
        this.regionId = regionId;
        this.vehicleType = vehicleType;
        this.acceptanceRate = 0.85 + Math.random() * 0.15;
        this.lastSeenAt = Instant.now();
        this.speedKmh = 0;
        this.heading = 0;
    }

    // ── Identity getters ────────────────────────────────────────────────
    public String getId() { return id; }
    public String getName() { return name; }
    public String getRegionId() { return regionId; }
    public VehicleType getVehicleType() { return vehicleType; }

    // ── Runtime state getters ───────────────────────────────────────────
    public GeoPoint getCurrentLocation() { return currentLocation; }
    public DriverState getState() { return state; }
    public String getActiveBundleId() { return activeBundleId; }
    public GeoPoint getTargetLocation() { return targetLocation; }
    public double getSpeedKmh() { return speedKmh; }
    public double getHeading() { return heading; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public List<String> getActiveOrderIds() { return List.copyOf(activeOrderIds); }
    public int getCurrentOrderCount() { return activeOrderIds.size(); }

    // ── Productivity getters ────────────────────────────────────────────
    public long getOnlineTicks() { return onlineTicks; }
    public long getBusyTicks() { return busyTicks; }
    public long getIdleTicks() { return idleTicks; }
    public double getDeadheadDistance() { return deadheadDistance; }
    public double getDeadheadTime() { return deadheadTime; }
    public int getCompletedOrders() { return completedOrders; }
    public int getBundlesCompleted() { return bundlesCompleted; }

    /** Online duration in simulated minutes (1 tick = 1 min). */
    public double getOnlineDuration() { return onlineTicks; }
    /** Busy duration in simulated minutes. */
    public double getBusyDuration() { return busyTicks; }
    /** Idle duration in simulated minutes. */
    public double getIdleDuration() { return idleTicks; }

    /** Driver utilization: busy time / online time. */
    public double getComputedUtilization() {
        return onlineTicks > 0 ? (double) busyTicks / onlineTicks : 0.0;
    }

    /** Orders per hour (simulated). */
    public double getOrdersPerHour() {
        double hours = onlineTicks / 60.0;
        return hours > 0 ? completedOrders / hours : 0.0;
    }

    /** Deadhead distance ratio: deadhead km / total km driven. */
    public double getDeadheadDistanceRatio() {
        double totalKm = deadheadDistance + (completedOrders * 3.0); // rough estimate
        return totalKm > 0 ? deadheadDistance / totalKm : 0.0;
    }

    // ── Economics getters ────────────────────────────────────────────────
    public double getGrossEarning() { return grossEarning; }
    public double getNetEarningToday() { return netEarningToday; }
    public double getFuelCostEstimate() { return fuelCostEstimate; }
    public double getAvgEarningPerHour() { return avgEarningPerHour; }

    // ── Quality getters ─────────────────────────────────────────────────
    public double getAcceptanceRate() { return acceptanceRate; }
    public double getCancellationRate() { return cancellationRate; }
    public double getReliabilityScore() { return reliabilityScore; }
    public double getUtilizationScore() { return utilizationScore; }

    // ── Prediction getters ──────────────────────────────────────────────
    public double getContinuationValueCurrentZone() { return continuationValueCurrentZone; }
    public double getShortageContribution() { return shortageContribution; }
    public double getOverloadRisk() { return overloadRisk; }
    public double getPredictedAcceptanceProb() { return predictedAcceptanceProb; }

    // ── State transitions ───────────────────────────────────────────────
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
    public void setActiveBundleId(String bundleId) { this.activeBundleId = bundleId; }

    // ── Productivity updates ────────────────────────────────────────────
    /** Call once per tick to accumulate time-based productivity metrics. */
    public void tickProductivity() {
        if (state == DriverState.OFFLINE) return;
        onlineTicks++;
        if (state == DriverState.ONLINE_IDLE) {
            idleTicks++;
        } else {
            busyTicks++;
        }
        // Update utilization score
        this.utilizationScore = getComputedUtilization();
        // Update avg earning per hour
        double hours = onlineTicks / 60.0;
        this.avgEarningPerHour = hours > 0 ? netEarningToday / hours : 0.0;
    }

    public void addDeadheadDistance(double km) { this.deadheadDistance += km; }
    public void addDeadheadTime(double minutes) { this.deadheadTime += minutes; }
    public void incrementCompletedOrders() { this.completedOrders++; }
    public void incrementBundlesCompleted() { this.bundlesCompleted++; }

    // ── Economics updates ────────────────────────────────────────────────
    public void addEarning(double amount) {
        this.grossEarning += amount;
        // Estimate fuel cost: ~2000 VND/km, assume 3km per order avg
        double estimatedFuel = 2000 * 3;
        this.fuelCostEstimate += estimatedFuel;
        this.netEarningToday = grossEarning - fuelCostEstimate;
    }

    // ── Prediction setters ──────────────────────────────────────────────
    public void setContinuationValueCurrentZone(double v) { this.continuationValueCurrentZone = v; }
    public void setShortageContribution(double v) { this.shortageContribution = v; }
    public void setOverloadRisk(double v) { this.overloadRisk = v; }
    public void setPredictedAcceptanceProb(double v) { this.predictedAcceptanceProb = v; }
    public void setUtilizationScore(double score) { this.utilizationScore = score; }
    public void setCancellationRate(double rate) { this.cancellationRate = rate; }
    public void setReliabilityScore(double score) { this.reliabilityScore = score; }

    // ── Route waypoints ─────────────────────────────────────────────────
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

    public GeoPoint getCurrentWaypoint() {
        synchronized (routeWaypoints) {
            if (currentWaypointIndex < routeWaypoints.size()) {
                return routeWaypoints.get(currentWaypointIndex);
            }
        }
        return null;
    }

    public void advanceWaypoint() {
        synchronized (routeWaypoints) {
            if (currentWaypointIndex < routeWaypoints.size()) {
                currentWaypointIndex++;
            }
        }
    }

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

    public List<double[]> getRemainingRoutePoints() {
        synchronized (routeWaypoints) {
            if (currentWaypointIndex >= routeWaypoints.size()) return Collections.emptyList();
            List<double[]> pts = new ArrayList<>(routeWaypoints.size() - currentWaypointIndex);
            for (int i = currentWaypointIndex; i < routeWaypoints.size(); i++) {
                GeoPoint p = routeWaypoints.get(i);
                pts.add(new double[]{p.lng(), p.lat()}); // MapBridge expects [lng, lat]
            }
            return pts;
        }
    }

    public boolean isAvailable() {
        return state == DriverState.ONLINE_IDLE && activeOrderIds.isEmpty();
    }
}

