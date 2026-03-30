package com.routechain.simulation;

import com.routechain.ai.StressRegime;
import com.routechain.domain.*;

import java.util.*;

/**
 * Layer 3 — Dispatch Plan data class.
 * Holds bundle + sequence + route + predictions + score for one complete plan.
 */
public class DispatchPlan {
    private final Driver driver;
    private final Bundle bundle;
    private final List<Stop> sequence;

    // ── Predictions ─────────────────────────────────────────────────────
    private double predictedTotalMinutes;
    private double predictedDeadheadKm;
    private double onTimeProbability;
    private double lateRisk;
    private double cancellationRisk;
    private double driverProfit;
    private double customerFee;
    private double bundleEfficiency;
    private double endZoneOpportunity;
    private double nextOrderAcquisitionScore;
    private double congestionPenalty;
    private double repositionPenalty;
    private double deliveryCorridorScore;
    private double lastDropLandingScore;
    private double expectedPostCompletionEmptyKm;
    private double remainingDropProximityScore;
    private double deliveryZigZagPenalty;
    private double expectedNextOrderIdleMinutes;
    private boolean stressFallbackOnly;
    private boolean waveLaunchEligible;
    private boolean waitingForThirdOrder;
    private boolean hardThreeOrderPolicyActive;
    private boolean harshWeatherStress;
    private StressRegime stressRegime = StressRegime.NORMAL;
    private String runId = "dispatch-live";
    private SelectionBucket selectionBucket = SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD;
    private int holdRemainingCycles;
    private String holdReason;
    private String holdAnchorZoneId;
    private double marginalDeadheadPerAddedOrder;
    private double pickupSpreadKm;
    private double waveReadinessScore;
    private double coverageQuality;
    private double replacementDepth;
    private double borrowedDependencyScore;
    private double emptyRiskAfter;
    private double executionScore;
    private double futureScore;
    private boolean executionGatePassed = true;

    // ── Final scoring ───────────────────────────────────────────────────
    private double totalScore;
    private double confidence;
    private String traceId;

    public DispatchPlan(Driver driver, Bundle bundle, List<Stop> sequence) {
        this.driver = driver;
        this.bundle = bundle;
        this.sequence = List.copyOf(sequence);
    }

    // ── Records ─────────────────────────────────────────────────────────

    /** A bundle of 1-5 orders. */
    public record Bundle(String bundleId, List<Order> orders, double gain, int size) {}

    /** A stop in the pickup/dropoff sequence. */
    public record Stop(String orderId, GeoPoint location, StopType type,
                       double estimatedArrivalMinutes) {
        public enum StopType { PICKUP, DROPOFF }
    }

    // ── Getters ─────────────────────────────────────────────────────────
    public Driver getDriver() { return driver; }
    public Bundle getBundle() { return bundle; }
    public List<Stop> getSequence() { return sequence; }
    public List<Order> getOrders() { return bundle.orders(); }
    public int getBundleSize() { return bundle.size(); }

    public double getPredictedTotalMinutes() { return predictedTotalMinutes; }
    public double getPredictedDeadheadKm() { return predictedDeadheadKm; }
    public double getOnTimeProbability() { return onTimeProbability; }
    public double getLateRisk() { return lateRisk; }
    public double getCancellationRisk() { return cancellationRisk; }
    public double getDriverProfit() { return driverProfit; }
    public double getCustomerFee() { return customerFee; }
    public double getBundleEfficiency() { return bundleEfficiency; }
    public double getEndZoneOpportunity() { return endZoneOpportunity; }
    public double getNextOrderAcquisitionScore() { return nextOrderAcquisitionScore; }
    public double getCongestionPenalty() { return congestionPenalty; }
    public double getRepositionPenalty() { return repositionPenalty; }
    public double getDeliveryCorridorScore() { return deliveryCorridorScore; }
    public double getLastDropLandingScore() { return lastDropLandingScore; }
    public double getExpectedPostCompletionEmptyKm() { return expectedPostCompletionEmptyKm; }
    public double getRemainingDropProximityScore() { return remainingDropProximityScore; }
    public double getDeliveryZigZagPenalty() { return deliveryZigZagPenalty; }
    public double getExpectedNextOrderIdleMinutes() { return expectedNextOrderIdleMinutes; }
    public boolean isStressFallbackOnly() { return stressFallbackOnly; }
    public boolean isWaveLaunchEligible() { return waveLaunchEligible; }
    public boolean isWaitingForThirdOrder() { return waitingForThirdOrder; }
    public boolean isHardThreeOrderPolicyActive() { return hardThreeOrderPolicyActive; }
    public boolean isHarshWeatherStress() { return harshWeatherStress; }
    public StressRegime getStressRegime() { return stressRegime; }
    public String getRunId() { return runId; }
    public SelectionBucket getSelectionBucket() { return selectionBucket; }
    public int getHoldRemainingCycles() { return holdRemainingCycles; }
    public String getHoldReason() { return holdReason; }
    public String getHoldAnchorZoneId() { return holdAnchorZoneId; }
    public double getMarginalDeadheadPerAddedOrder() { return marginalDeadheadPerAddedOrder; }
    public double getPickupSpreadKm() { return pickupSpreadKm; }
    public double getWaveReadinessScore() { return waveReadinessScore; }
    public double getCoverageQuality() { return coverageQuality; }
    public double getReplacementDepth() { return replacementDepth; }
    public double getBorrowedDependencyScore() { return borrowedDependencyScore; }
    public double getEmptyRiskAfter() { return emptyRiskAfter; }
    public double getExecutionScore() { return executionScore; }
    public double getFutureScore() { return futureScore; }
    public boolean isExecutionGatePassed() { return executionGatePassed; }
    public double getTotalScore() { return totalScore; }
    public double getConfidence() { return confidence; }
    public String getTraceId() { return traceId; }

    /** Get the last dropoff point (end zone). */
    public GeoPoint getEndZonePoint() {
        for (int i = sequence.size() - 1; i >= 0; i--) {
            if (sequence.get(i).type() == Stop.StopType.DROPOFF) {
                return sequence.get(i).location();
            }
        }
        return bundle.orders().get(bundle.orders().size() - 1).getDropoffPoint();
    }

    // ── Setters ─────────────────────────────────────────────────────────
    public void setPredictedTotalMinutes(double v) { this.predictedTotalMinutes = v; }
    public void setPredictedDeadheadKm(double v) { this.predictedDeadheadKm = v; }
    public void setOnTimeProbability(double v) { this.onTimeProbability = v; }
    public void setLateRisk(double v) { this.lateRisk = v; }
    public void setCancellationRisk(double v) { this.cancellationRisk = v; }
    public void setDriverProfit(double v) { this.driverProfit = v; }
    public void setCustomerFee(double v) { this.customerFee = v; }
    public void setBundleEfficiency(double v) { this.bundleEfficiency = v; }
    public void setEndZoneOpportunity(double v) { this.endZoneOpportunity = v; }
    public void setNextOrderAcquisitionScore(double v) { this.nextOrderAcquisitionScore = v; }
    public void setCongestionPenalty(double v) { this.congestionPenalty = v; }
    public void setRepositionPenalty(double v) { this.repositionPenalty = v; }
    public void setDeliveryCorridorScore(double v) { this.deliveryCorridorScore = v; }
    public void setLastDropLandingScore(double v) { this.lastDropLandingScore = v; }
    public void setExpectedPostCompletionEmptyKm(double v) { this.expectedPostCompletionEmptyKm = v; }
    public void setRemainingDropProximityScore(double v) { this.remainingDropProximityScore = v; }
    public void setDeliveryZigZagPenalty(double v) { this.deliveryZigZagPenalty = v; }
    public void setExpectedNextOrderIdleMinutes(double v) { this.expectedNextOrderIdleMinutes = v; }
    public void setStressFallbackOnly(boolean v) { this.stressFallbackOnly = v; }
    public void setWaveLaunchEligible(boolean v) { this.waveLaunchEligible = v; }
    public void setWaitingForThirdOrder(boolean v) { this.waitingForThirdOrder = v; }
    public void setHardThreeOrderPolicyActive(boolean v) { this.hardThreeOrderPolicyActive = v; }
    public void setHarshWeatherStress(boolean v) { this.harshWeatherStress = v; }
    public void setStressRegime(StressRegime v) {
        this.stressRegime = v == null ? StressRegime.NORMAL : v;
    }
    public void setRunId(String runId) {
        this.runId = (runId == null || runId.isBlank()) ? "dispatch-live" : runId;
    }
    public void setSelectionBucket(SelectionBucket selectionBucket) {
        this.selectionBucket = selectionBucket == null
                ? SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                : selectionBucket;
    }
    public void setHoldRemainingCycles(int holdRemainingCycles) {
        this.holdRemainingCycles = Math.max(0, holdRemainingCycles);
    }
    public void setHoldReason(String holdReason) { this.holdReason = holdReason; }
    public void setHoldAnchorZoneId(String holdAnchorZoneId) { this.holdAnchorZoneId = holdAnchorZoneId; }
    public void setMarginalDeadheadPerAddedOrder(double value) { this.marginalDeadheadPerAddedOrder = value; }
    public void setPickupSpreadKm(double pickupSpreadKm) { this.pickupSpreadKm = pickupSpreadKm; }
    public void setWaveReadinessScore(double waveReadinessScore) { this.waveReadinessScore = waveReadinessScore; }
    public void setCoverageQuality(double coverageQuality) { this.coverageQuality = coverageQuality; }
    public void setReplacementDepth(double replacementDepth) { this.replacementDepth = replacementDepth; }
    public void setBorrowedDependencyScore(double borrowedDependencyScore) {
        this.borrowedDependencyScore = borrowedDependencyScore;
    }
    public void setEmptyRiskAfter(double emptyRiskAfter) { this.emptyRiskAfter = emptyRiskAfter; }
    public void setExecutionScore(double executionScore) { this.executionScore = executionScore; }
    public void setFutureScore(double futureScore) { this.futureScore = futureScore; }
    public void setExecutionGatePassed(boolean executionGatePassed) {
        this.executionGatePassed = executionGatePassed;
    }
    public void setTotalScore(double v) { this.totalScore = v; }
    public void setConfidence(double v) { this.confidence = v; }
    public void setTraceId(String v) { this.traceId = v; }

    @Override
    public String toString() {
        return String.format("Plan[%s→bundle(%d) score=%.3f conf=%.2f]",
                driver.getId(), bundle.size(), totalScore, confidence);
    }
}
