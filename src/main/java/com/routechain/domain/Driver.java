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

    // ── Enums for motion state machine ───────────────────────────────────
    public enum MovementMode {
        FREE_FLOW, APPROACH_INTERSECTION, QUEUED, MERCHANT_WAIT, STOP_GO
    }

    public enum DriverStyleProfile {
        CAUTIOUS, NORMAL, AGGRESSIVE
    }

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
    private volatile int redLightWaitTicks = 0;

    // ── Motion state machine (DriverMotionEngine) ───────────────────────
    private volatile MovementMode movementMode = MovementMode.FREE_FLOW;
    private volatile DriverStyleProfile driverStyleProfile = DriverStyleProfile.NORMAL;
    private volatile String currentCorridorId;
    private volatile double currentTrafficExposure = 0.0;
    private volatile int microDelayTicksRemaining = 0;
    private volatile int queueTicksRemaining = 0;
    private volatile int merchantWaitTicksRemaining = 0;
    private volatile double fatigueLevel = 0.0;
    private volatile String preferredZoneType;
    private final List<String> activeOrderIds = new ArrayList<>();

    public int getRedLightWaitTicks() { return redLightWaitTicks; }
    public void setRedLightWaitTicks(int ticks) { this.redLightWaitTicks = ticks; }
    public void decreaseRedLightWait() { if (redLightWaitTicks > 0) redLightWaitTicks--; }

    // ── Route waypoints ─────────────────────────────────────────────────
    private final List<GeoPoint> routeWaypoints = new ArrayList<>();
    private volatile int currentWaypointIndex = 0;
    private volatile String routeRequestId;
    private volatile int routeLatencyTicksRemaining = 0;
    private volatile GeoPoint pendingTargetLocation;
    private volatile DriverState pendingRouteState;
    private volatile int pendingSequenceIndex = -1;
    private volatile int activeRouteSequenceIndex = -1;
    private volatile boolean firstPickupCompleted = false;
    private volatile boolean routeLockedAfterFirstPickup = false;

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

        // Randomize driver style
        double roll = Math.random();
        this.driverStyleProfile = roll < 0.25 ? DriverStyleProfile.CAUTIOUS
                : roll < 0.75 ? DriverStyleProfile.NORMAL : DriverStyleProfile.AGGRESSIVE;
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
    public GeoPoint getPendingTargetLocation() { return pendingTargetLocation; }
    public double getSpeedKmh() { return speedKmh; }
    public double getHeading() { return heading; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public List<String> getActiveOrderIds() { return List.copyOf(activeOrderIds); }
    public int getCurrentOrderCount() { return activeOrderIds.size(); }
    public String getRouteRequestId() { return routeRequestId; }
    public int getRouteLatencyTicksRemaining() { return routeLatencyTicksRemaining; }
    public DriverState getPendingRouteState() { return pendingRouteState; }
    public int getPendingSequenceIndex() { return pendingSequenceIndex; }
    public int getActiveRouteSequenceIndex() { return activeRouteSequenceIndex; }
    public boolean hasCompletedFirstPickup() { return firstPickupCompleted; }

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
    public void setPendingTargetLocation(GeoPoint target) { this.pendingTargetLocation = target; }
    public void setSpeedKmh(double speed) { this.speedKmh = speed; }
    public void setHeading(double heading) { this.heading = heading; }
    public void addOrder(String orderId) {
        if (orderId != null && !activeOrderIds.contains(orderId)) {
            activeOrderIds.add(orderId);
        }
    }
    public void removeOrder(String orderId) { activeOrderIds.remove(orderId); }
    public void setActiveBundleId(String bundleId) { this.activeBundleId = bundleId; }
    public void setRouteRequestId(String routeRequestId) { this.routeRequestId = routeRequestId; }
    public void setRouteLatencyTicksRemaining(int routeLatencyTicksRemaining) {
        this.routeLatencyTicksRemaining = Math.max(0, routeLatencyTicksRemaining);
    }
    public void setPendingRouteState(DriverState pendingRouteState) {
        this.pendingRouteState = pendingRouteState;
    }

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

    // ── AI Dispatch Sequence ────────────────────────────────────────────
    private List<com.routechain.simulation.DispatchPlan.Stop> assignedSequence;
    private int currentSequenceIndex = 0;

    public void setAssignedSequence(List<com.routechain.simulation.DispatchPlan.Stop> sequence) {
        setAssignedSequence(sequence, true);
    }

    public void replaceAssignedSequenceBeforeFirstPickup(List<com.routechain.simulation.DispatchPlan.Stop> sequence) {
        setAssignedSequence(sequence, !routeLockedAfterFirstPickup);
    }

    private void setAssignedSequence(List<com.routechain.simulation.DispatchPlan.Stop> sequence,
                                     boolean resetPickupProgress) {
        this.assignedSequence = sequence != null ? List.copyOf(sequence) : null;
        this.currentSequenceIndex = 0;
        if (resetPickupProgress) {
            this.firstPickupCompleted = false;
            this.routeLockedAfterFirstPickup = false;
        }
        this.activeRouteSequenceIndex = -1;
        if (sequence == null) {
            clearPendingRoute();
        }
    }

    public List<com.routechain.simulation.DispatchPlan.Stop> getAssignedSequence() {
        return assignedSequence;
    }

    public int getCurrentSequenceIndex() {
        return currentSequenceIndex;
    }

    public void advanceSequenceIndex() {
        this.currentSequenceIndex++;
    }

    public boolean isAvailable() {
        return state == DriverState.ONLINE_IDLE && activeOrderIds.isEmpty();
    }

    public boolean isPrePickupAugmentable() {
        if (assignedSequence == null || assignedSequence.isEmpty()) {
            return false;
        }
        if (routeLockedAfterFirstPickup || currentSequenceIndex > 0) {
            return false;
        }
        return !activeOrderIds.isEmpty()
                && (state == DriverState.ROUTE_PENDING
                || state == DriverState.PICKUP_EN_ROUTE
                || state == DriverState.WAITING_PICKUP);
    }

    public boolean isRouteLockedAfterFirstPickup() {
        return routeLockedAfterFirstPickup;
    }

    public void prepareRouteRequest(String requestId,
                                    GeoPoint pendingTargetLocation,
                                    DriverState pendingRouteState,
                                    int latencyTicks) {
        prepareRouteRequest(requestId, pendingTargetLocation, pendingRouteState, latencyTicks, -1);
    }

    public void prepareRouteRequest(String requestId,
                                    GeoPoint pendingTargetLocation,
                                    DriverState pendingRouteState,
                                    int latencyTicks,
                                    int sequenceIndex) {
        clearRouteWaypoints();
        this.targetLocation = null;
        this.routeRequestId = requestId;
        this.pendingTargetLocation = pendingTargetLocation;
        this.pendingRouteState = pendingRouteState;
        this.routeLatencyTicksRemaining = Math.max(0, latencyTicks);
        this.pendingSequenceIndex = sequenceIndex;
        this.activeRouteSequenceIndex = -1;
    }

    public void tickRouteLatency() {
        if (routeLatencyTicksRemaining > 0) {
            routeLatencyTicksRemaining--;
        }
    }

    public boolean isRouteReadyForActivation() {
        return pendingTargetLocation != null
                && pendingRouteState != null
                && routeLatencyTicksRemaining <= 0
                && hasRouteWaypoints();
    }

    public DriverState activatePendingRoute() {
        DriverState targetState = pendingRouteState;
        this.targetLocation = pendingTargetLocation;
        this.activeRouteSequenceIndex = pendingSequenceIndex;
        this.pendingTargetLocation = null;
        this.pendingRouteState = null;
        this.routeLatencyTicksRemaining = 0;
        this.routeRequestId = null;
        this.pendingSequenceIndex = -1;
        return targetState;
    }

    public void clearPendingRoute() {
        this.routeRequestId = null;
        this.routeLatencyTicksRemaining = 0;
        this.pendingTargetLocation = null;
        this.pendingRouteState = null;
        this.pendingSequenceIndex = -1;
        this.activeRouteSequenceIndex = -1;
    }

    public void markFirstPickupCompleted() {
        if (!firstPickupCompleted) {
            this.firstPickupCompleted = true;
            this.routeLockedAfterFirstPickup = true;
        }
    }

    // ── Motion state machine getters/setters ────────────────────────────
    public MovementMode getMovementMode() { return movementMode; }
    public void setMovementMode(MovementMode mode) { this.movementMode = mode; }

    public DriverStyleProfile getDriverStyleProfile() { return driverStyleProfile; }
    public void setDriverStyleProfile(DriverStyleProfile profile) { this.driverStyleProfile = profile; }

    public String getCurrentCorridorId() { return currentCorridorId; }
    public void setCurrentCorridorId(String corridorId) { this.currentCorridorId = corridorId; }

    public double getCurrentTrafficExposure() { return currentTrafficExposure; }
    public void setCurrentTrafficExposure(double exposure) { this.currentTrafficExposure = exposure; }

    public int getMicroDelayTicksRemaining() { return microDelayTicksRemaining; }
    public void setMicroDelayTicksRemaining(int ticks) { this.microDelayTicksRemaining = ticks; }

    public int getQueueTicksRemaining() { return queueTicksRemaining; }
    public void setQueueTicksRemaining(int ticks) { this.queueTicksRemaining = ticks; }

    public int getMerchantWaitTicksRemaining() { return merchantWaitTicksRemaining; }
    public void setMerchantWaitTicksRemaining(int ticks) { this.merchantWaitTicksRemaining = ticks; }

    public double getFatigueLevel() { return fatigueLevel; }
    public void setFatigueLevel(double level) { this.fatigueLevel = Math.max(0, Math.min(1.0, level)); }

    public String getPreferredZoneType() { return preferredZoneType; }
    public void setPreferredZoneType(String type) { this.preferredZoneType = type; }

    public List<GeoPoint> getRouteWaypoints() {
        synchronized (routeWaypoints) {
            return new ArrayList<>(routeWaypoints);
        }
    }
}
