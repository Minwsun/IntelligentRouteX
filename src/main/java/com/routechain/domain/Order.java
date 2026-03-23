package com.routechain.domain;

import com.routechain.domain.Enums.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Order entity — tracks a delivery from quote to completion.
 * Full lifecycle tracking for KPI computation and replay audit.
 */
public class Order {
    // ── Identity ────────────────────────────────────────────────────────
    private final String id;
    private final String customerId;
    private final String correlationId;
    private volatile String decisionTraceId;

    // ── Location ────────────────────────────────────────────────────────
    private final String pickupRegionId;
    private final GeoPoint pickupPoint;
    private final GeoPoint dropoffPoint;
    private final String dropoffRegionId;

    // ── Timing (full lifecycle) ─────────────────────────────────────────
    private final Instant createdAt;
    private volatile Instant confirmedAt;
    private volatile Instant assignedAt;
    private volatile Instant pickupStartedAt;
    private volatile Instant pickedUpAt;
    private volatile Instant dropoffStartedAt;
    private volatile Instant deliveredAt;
    private volatile Instant cancelledAt;
    private volatile Instant failedAt;

    // ── Business ────────────────────────────────────────────────────────
    private final String serviceType;
    private final int priority;
    private final double quotedFee;
    private volatile double actualFee;
    private final int promisedEtaMinutes;
    private volatile OrderStatus status;
    private volatile String assignedDriverId;
    private volatile String bundleId;
    private volatile String cancellationReason;
    private volatile String failureReason;

    // ── Prediction / Risk ───────────────────────────────────────────────
    private volatile double cancellationRisk;
    private volatile double predictedWaitTime;
    private volatile double predictedTravelTime;
    private volatile double predictedLateRisk;
    private volatile double predictedBundleFit;
    private volatile double predictedAssignmentConfidence;

    // ── Merchant readiness (MerchantWaitEngine) ─────────────────────────
    private volatile String merchantId;
    private volatile String pickupClusterId;
    private volatile Instant predictedReadyAt;
    private volatile Instant actualReadyAt;
    private volatile double pickupDelayHazard;

    public Order(String id, String customerId, String pickupRegionId,
                 GeoPoint pickupPoint, GeoPoint dropoffPoint, String dropoffRegionId,
                 double quotedFee, int promisedEtaMinutes) {
        this(id, customerId, pickupRegionId, pickupPoint, dropoffPoint,
                dropoffRegionId, quotedFee, promisedEtaMinutes, Instant.now());
    }

    public Order(String id, String customerId, String pickupRegionId,
                 GeoPoint pickupPoint, GeoPoint dropoffPoint, String dropoffRegionId,
                 double quotedFee, int promisedEtaMinutes, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.correlationId = UUID.randomUUID().toString().substring(0, 8);
        this.pickupRegionId = pickupRegionId;
        this.pickupPoint = pickupPoint;
        this.dropoffPoint = dropoffPoint;
        this.dropoffRegionId = dropoffRegionId;
        this.status = OrderStatus.CONFIRMED;
        this.serviceType = "standard";
        this.priority = 0;
        this.quotedFee = quotedFee;
        this.actualFee = quotedFee;
        this.promisedEtaMinutes = promisedEtaMinutes;
        this.cancellationRisk = 0.0;
        this.createdAt = createdAt;
        this.confirmedAt = createdAt;
    }

    // ── Identity getters ────────────────────────────────────────────────
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getCorrelationId() { return correlationId; }
    public String getDecisionTraceId() { return decisionTraceId; }

    // ── Location getters ────────────────────────────────────────────────
    public String getPickupRegionId() { return pickupRegionId; }
    public GeoPoint getPickupPoint() { return pickupPoint; }
    public GeoPoint getDropoffPoint() { return dropoffPoint; }
    public String getDropoffRegionId() { return dropoffRegionId; }

    // ── Timing getters ──────────────────────────────────────────────────
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getPickupStartedAt() { return pickupStartedAt; }
    public Instant getPickedUpAt() { return pickedUpAt; }
    public Instant getDropoffStartedAt() { return dropoffStartedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getFailedAt() { return failedAt; }

    // ── Business getters ────────────────────────────────────────────────
    public OrderStatus getStatus() { return status; }
    public String getAssignedDriverId() { return assignedDriverId; }
    public String getBundleId() { return bundleId; }
    public double getQuotedFee() { return quotedFee; }
    public double getActualFee() { return actualFee; }
    public int getPromisedEtaMinutes() { return promisedEtaMinutes; }
    public String getServiceType() { return serviceType; }
    public int getPriority() { return priority; }
    public String getCancellationReason() { return cancellationReason; }
    public String getFailureReason() { return failureReason; }

    // ── Prediction getters ──────────────────────────────────────────────
    public double getCancellationRisk() { return cancellationRisk; }
    public double getPredictedWaitTime() { return predictedWaitTime; }
    public double getPredictedTravelTime() { return predictedTravelTime; }
    public double getPredictedLateRisk() { return predictedLateRisk; }
    public double getPredictedBundleFit() { return predictedBundleFit; }
    public double getPredictedAssignmentConfidence() { return predictedAssignmentConfidence; }

    // ── Transitions ─────────────────────────────────────────────────────
    public void assignDriver(String driverId) {
        assignDriver(driverId, Instant.now());
    }

    public void assignDriver(String driverId, Instant assignedAt) {
        this.assignedDriverId = driverId;
        this.assignedAt = assignedAt;
        this.status = OrderStatus.ASSIGNED;
    }

    public void markPickupStarted() {
        markPickupStarted(Instant.now());
    }

    public void markPickupStarted(Instant pickupStartedAt) {
        this.pickupStartedAt = pickupStartedAt;
        this.status = OrderStatus.PICKUP_EN_ROUTE;
    }

    public void markPickedUp() {
        markPickedUp(Instant.now());
    }

    public void markPickedUp(Instant pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
        this.status = OrderStatus.PICKED_UP;
    }

    public void markDropoffStarted() {
        markDropoffStarted(Instant.now());
    }

    public void markDropoffStarted(Instant dropoffStartedAt) {
        this.dropoffStartedAt = dropoffStartedAt;
        this.status = OrderStatus.DROPOFF_EN_ROUTE;
    }

    public void markDelivered() {
        markDelivered(Instant.now());
    }

    public void markDelivered(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
        this.status = OrderStatus.DELIVERED;
    }

    public void markCancelled(String reason) {
        markCancelled(reason, Instant.now());
    }

    public void markCancelled(String reason, Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
        this.cancellationReason = reason;
        this.status = OrderStatus.CANCELLED;
    }

    public void markFailed(String reason) {
        markFailed(reason, Instant.now());
    }

    public void markFailed(String reason, Instant failedAt) {
        this.failedAt = failedAt;
        this.failureReason = reason;
        this.status = OrderStatus.FAILED;
    }

    /** Check if delivery was late relative to promised ETA. */
    public boolean isLate() {
        if (deliveredAt == null || createdAt == null) return false;
        long actualMinutes = java.time.Duration.between(createdAt, deliveredAt).toMinutes();
        return actualMinutes > promisedEtaMinutes;
    }

    /** Get assignment latency in milliseconds. Returns -1 if not assigned. */
    public long getAssignmentLatencyMs() {
        if (assignedAt == null || confirmedAt == null) return -1;
        return java.time.Duration.between(confirmedAt, assignedAt).toMillis();
    }

    // ── Setters ─────────────────────────────────────────────────────────
    public void setBundle(String bundleId) { this.bundleId = bundleId; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setCancellationRisk(double risk) { this.cancellationRisk = risk; }
    public void setActualFee(double fee) { this.actualFee = fee; }
    public void setDecisionTraceId(String id) { this.decisionTraceId = id; }
    public void setPredictedWaitTime(double t) { this.predictedWaitTime = t; }
    public void setPredictedTravelTime(double t) { this.predictedTravelTime = t; }
    public void setPredictedLateRisk(double r) { this.predictedLateRisk = r; }
    public void setPredictedBundleFit(double f) { this.predictedBundleFit = f; }
    public void setPredictedAssignmentConfidence(double v) { this.predictedAssignmentConfidence = v; }

    // ── Merchant readiness getters/setters ───────────────────────────────
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String id) { this.merchantId = id; }

    public String getPickupClusterId() { return pickupClusterId; }
    public void setPickupClusterId(String id) { this.pickupClusterId = id; }

    public Instant getPredictedReadyAt() { return predictedReadyAt; }
    public void setPredictedReadyAt(Instant t) { this.predictedReadyAt = t; }

    public Instant getActualReadyAt() { return actualReadyAt; }
    public void setActualReadyAt(Instant t) { this.actualReadyAt = t; }

    public double getPickupDelayHazard() { return pickupDelayHazard; }
    public void setPickupDelayHazard(double h) { this.pickupDelayHazard = h; }
}
