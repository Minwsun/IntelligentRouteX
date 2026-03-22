package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;

/**
 * Represents a candidate assignment: one driver assigned to one or more orders.
 * Scored by DispatchScorer with multi-criteria evaluation.
 */
public class CandidateAssignment {
    private final Driver driver;
    private final Order order;
    private final String bundleId;   // null if single-order assignment

    // ── Scoring components ──────────────────────────────────────────────
    private double onTimeProbability = 0.0;
    private double bundleGain = 0.0;
    private double continuationValue = 0.0;
    private double earningScore = 0.0;
    private double deadheadCost = 0.0;
    private double lateRisk = 0.0;
    private double overloadRisk = 0.0;
    private double cancellationRisk = 0.0;

    // ── Final score ─────────────────────────────────────────────────────
    private double totalScore = 0.0;
    private double confidence = 0.0;

    // ── Raw metrics ─────────────────────────────────────────────────────
    private double distanceToPickupKm = 0.0;
    private double estimatedPickupMinutes = 0.0;
    private double estimatedDeliveryMinutes = 0.0;

    public CandidateAssignment(Driver driver, Order order, String bundleId) {
        this.driver = driver;
        this.order = order;
        this.bundleId = bundleId;
    }

    public CandidateAssignment(Driver driver, Order order) {
        this(driver, order, null);
    }

    // ── Getters ─────────────────────────────────────────────────────────
    public Driver getDriver() { return driver; }
    public Order getOrder() { return order; }
    public String getBundleId() { return bundleId; }
    public boolean isBundle() { return bundleId != null; }

    public double getOnTimeProbability() { return onTimeProbability; }
    public double getBundleGain() { return bundleGain; }
    public double getContinuationValue() { return continuationValue; }
    public double getEarningScore() { return earningScore; }
    public double getDeadheadCost() { return deadheadCost; }
    public double getLateRisk() { return lateRisk; }
    public double getOverloadRisk() { return overloadRisk; }
    public double getCancellationRisk() { return cancellationRisk; }
    public double getTotalScore() { return totalScore; }
    public double getConfidence() { return confidence; }
    public double getDistanceToPickupKm() { return distanceToPickupKm; }
    public double getEstimatedPickupMinutes() { return estimatedPickupMinutes; }
    public double getEstimatedDeliveryMinutes() { return estimatedDeliveryMinutes; }

    // ── Setters ─────────────────────────────────────────────────────────
    public void setOnTimeProbability(double v) { this.onTimeProbability = v; }
    public void setBundleGain(double v) { this.bundleGain = v; }
    public void setContinuationValue(double v) { this.continuationValue = v; }
    public void setEarningScore(double v) { this.earningScore = v; }
    public void setDeadheadCost(double v) { this.deadheadCost = v; }
    public void setLateRisk(double v) { this.lateRisk = v; }
    public void setOverloadRisk(double v) { this.overloadRisk = v; }
    public void setCancellationRisk(double v) { this.cancellationRisk = v; }
    public void setTotalScore(double v) { this.totalScore = v; }
    public void setConfidence(double v) { this.confidence = v; }
    public void setDistanceToPickupKm(double v) { this.distanceToPickupKm = v; }
    public void setEstimatedPickupMinutes(double v) { this.estimatedPickupMinutes = v; }
    public void setEstimatedDeliveryMinutes(double v) { this.estimatedDeliveryMinutes = v; }

    @Override
    public String toString() {
        return String.format("Candidate[%s -> %s] score=%.3f conf=%.2f",
                driver.getId(), order.getId(), totalScore, confidence);
    }
}
