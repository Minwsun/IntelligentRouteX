package com.routechain.domain;

import com.routechain.domain.Enums.*;
import java.time.Instant;
import java.util.List;

/**
 * Order entity — tracks a delivery from quote to completion.
 */
public class Order {
    private final String id;
    private final String customerId;
    private final String pickupRegionId;
    private final GeoPoint pickupPoint;
    private final GeoPoint dropoffPoint;
    private final String dropoffRegionId;
    private volatile OrderStatus status;
    private volatile String assignedDriverId;
    private volatile String bundleId;
    private final double quotedFee;
    private final int promisedEtaMinutes;
    private volatile double cancellationRisk;
    private final Instant createdAt;
    private volatile Instant confirmedAt;
    private volatile Instant deliveredAt;

    public Order(String id, String customerId, String pickupRegionId,
                 GeoPoint pickupPoint, GeoPoint dropoffPoint, String dropoffRegionId,
                 double quotedFee, int promisedEtaMinutes) {
        this.id = id;
        this.customerId = customerId;
        this.pickupRegionId = pickupRegionId;
        this.pickupPoint = pickupPoint;
        this.dropoffPoint = dropoffPoint;
        this.dropoffRegionId = dropoffRegionId;
        this.status = OrderStatus.CONFIRMED;
        this.quotedFee = quotedFee;
        this.promisedEtaMinutes = promisedEtaMinutes;
        this.cancellationRisk = 0.0;
        this.createdAt = Instant.now();
        this.confirmedAt = Instant.now();
    }

    // Getters
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getPickupRegionId() { return pickupRegionId; }
    public GeoPoint getPickupPoint() { return pickupPoint; }
    public GeoPoint getDropoffPoint() { return dropoffPoint; }
    public String getDropoffRegionId() { return dropoffRegionId; }
    public OrderStatus getStatus() { return status; }
    public String getAssignedDriverId() { return assignedDriverId; }
    public String getBundleId() { return bundleId; }
    public double getQuotedFee() { return quotedFee; }
    public int getPromisedEtaMinutes() { return promisedEtaMinutes; }
    public double getCancellationRisk() { return cancellationRisk; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }

    // Transitions
    public void assignDriver(String driverId) {
        this.assignedDriverId = driverId;
        this.status = OrderStatus.ASSIGNED;
    }

    public void setBundle(String bundleId) { this.bundleId = bundleId; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setCancellationRisk(double risk) { this.cancellationRisk = risk; }
    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }
}
