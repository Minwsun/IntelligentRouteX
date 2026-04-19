package com.routechain.simulator.demand;

import com.routechain.domain.GeoPoint;

import java.time.Instant;

public final class SimOrder {
    private final String orderId;
    private final String merchantId;
    private final GeoPoint pickupPoint;
    private final GeoPoint dropoffPoint;
    private final Instant createdAt;
    private final Instant readyAt;
    private final int promisedEtaMinutes;
    private SimOrderStatus status;
    private String assignmentId;
    private String traceId;
    private Instant deliveredAt;
    private long pickupTravelSeconds;
    private long merchantWaitSeconds;
    private long dropoffTravelSeconds;
    private long trafficDelaySeconds;
    private String weatherModifier;

    public SimOrder(String orderId,
                    String merchantId,
                    GeoPoint pickupPoint,
                    GeoPoint dropoffPoint,
                    Instant createdAt,
                    Instant readyAt,
                    int promisedEtaMinutes,
                    SimOrderStatus status) {
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.pickupPoint = pickupPoint;
        this.dropoffPoint = dropoffPoint;
        this.createdAt = createdAt;
        this.readyAt = readyAt;
        this.promisedEtaMinutes = promisedEtaMinutes;
        this.status = status;
    }

    public String orderId() {
        return orderId;
    }

    public String merchantId() {
        return merchantId;
    }

    public GeoPoint pickupPoint() {
        return pickupPoint;
    }

    public GeoPoint dropoffPoint() {
        return dropoffPoint;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant readyAt() {
        return readyAt;
    }

    public int promisedEtaMinutes() {
        return promisedEtaMinutes;
    }

    public SimOrderStatus status() {
        return status;
    }

    public void status(SimOrderStatus status) {
        this.status = status;
    }

    public String assignmentId() {
        return assignmentId;
    }

    public void assignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String traceId() {
        return traceId;
    }

    public void traceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public void deliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public long pickupTravelSeconds() {
        return pickupTravelSeconds;
    }

    public void pickupTravelSeconds(long pickupTravelSeconds) {
        this.pickupTravelSeconds = pickupTravelSeconds;
    }

    public long merchantWaitSeconds() {
        return merchantWaitSeconds;
    }

    public void merchantWaitSeconds(long merchantWaitSeconds) {
        this.merchantWaitSeconds = merchantWaitSeconds;
    }

    public long dropoffTravelSeconds() {
        return dropoffTravelSeconds;
    }

    public void dropoffTravelSeconds(long dropoffTravelSeconds) {
        this.dropoffTravelSeconds = dropoffTravelSeconds;
    }

    public long trafficDelaySeconds() {
        return trafficDelaySeconds;
    }

    public void trafficDelaySeconds(long trafficDelaySeconds) {
        this.trafficDelaySeconds = trafficDelaySeconds;
    }

    public String weatherModifier() {
        return weatherModifier;
    }

    public void weatherModifier(String weatherModifier) {
        this.weatherModifier = weatherModifier;
    }
}
