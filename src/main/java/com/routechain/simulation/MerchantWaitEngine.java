package com.routechain.simulation;

import com.routechain.domain.Order;
import com.routechain.domain.Region;

import java.time.Instant;
import java.util.Random;

/**
 * MerchantWaitEngine — models merchant preparation time and readiness.
 *
 * Each order gets:
 *   - predictedReadyAt: createdAt + prepTime (zone-dependent, 3-15min)
 *   - actualReadyAt: predictedReadyAt + delay (Exponential distribution)
 *   - pickupDelayHazard: P(significant delay)
 *
 * Used by SequenceOptimizer for feasibility checks
 * and by PlanUtilityScorer for merchant wait penalty.
 */
public class MerchantWaitEngine {

    /** Base preparation time range by zone type (minutes). */
    private static final double PREP_MIN_FOOD = 5.0;
    private static final double PREP_MAX_FOOD = 12.0;
    private static final double PREP_MIN_MART = 3.0;
    private static final double PREP_MAX_MART = 8.0;
    private static final double PREP_MIN_DEFAULT = 2.0;
    private static final double PREP_MAX_DEFAULT = 6.0;

    /** Mean delay (minutes) added on top of prep time (Exponential). */
    private static final double MEAN_DELAY_MINUTES = 2.0;

    /** Hazard threshold: fraction of orders with > threshold delay. */
    private static final double DELAY_HAZARD_THRESHOLD_MINUTES = 5.0;

    private final Random rng;

    public MerchantWaitEngine(Random rng) {
        this.rng = rng;
    }

    /**
     * Assign merchant readiness timing to an order at creation time.
     *
     * @param order the newly created order
     * @param region the pickup region (for zone type)
     */
    public void assignMerchantTiming(Order order, Region region) {
        double prepMin;
        double prepMax;

        if (region.getZoneType() == Region.ZoneType.CBD
                || region.getZoneType() == Region.ZoneType.MIXED) {
            prepMin = PREP_MIN_FOOD;
            prepMax = PREP_MAX_FOOD;
        } else if (region.getZoneType() == Region.ZoneType.INDUSTRIAL) {
            prepMin = PREP_MIN_MART;
            prepMax = PREP_MAX_MART;
        } else {
            prepMin = PREP_MIN_DEFAULT;
            prepMax = PREP_MAX_DEFAULT;
        }

        // Base preparation time (uniform)
        double prepTimeMinutes = prepMin + rng.nextDouble() * (prepMax - prepMin);

        // Delay: Exponential(mean=2 min)
        double delayMinutes = -MEAN_DELAY_MINUTES * Math.log(1 - rng.nextDouble());
        delayMinutes = Math.max(0, delayMinutes);

        Instant createdAt = order.getCreatedAt();
        Instant predictedReady = createdAt.plusSeconds((long) (prepTimeMinutes * 60));
        Instant actualReady = predictedReady.plusSeconds((long) (delayMinutes * 60));

        // Hazard: probability that delay exceeds threshold
        // For Exponential, P(X > t) = exp(-t/mean)
        double hazard = Math.exp(-DELAY_HAZARD_THRESHOLD_MINUTES / MEAN_DELAY_MINUTES);

        order.setPredictedReadyAt(predictedReady);
        order.setActualReadyAt(actualReady);
        order.setPickupDelayHazard(hazard);

        // Assign merchant ID based on region
        String merchantId = "MERCH-" + region.getId() + "-" + (rng.nextInt(5) + 1);
        order.setMerchantId(merchantId);
    }

    /**
     * Check if merchant is ready for pickup at a given moment.
     *
     * @param order the order to check
     * @param currentTime the current simulated time
     * @return true if merchant is ready (actualReadyAt <= currentTime)
     */
    public boolean isMerchantReady(Order order, Instant currentTime) {
        if (order.getActualReadyAt() == null) return true; // no merchant data
        return !currentTime.isBefore(order.getActualReadyAt());
    }

    /**
     * Estimate wait minutes until merchant is ready.
     *
     * @param order the order
     * @param currentTime the current time
     * @return estimated minutes to wait (0 if already ready)
     */
    public double estimateWaitMinutes(Order order, Instant currentTime) {
        if (order.getActualReadyAt() == null) return 0;
        if (!currentTime.isBefore(order.getActualReadyAt())) return 0;

        long diffSeconds = java.time.Duration.between(currentTime, order.getActualReadyAt()).getSeconds();
        return diffSeconds / 60.0;
    }

    /**
     * Estimate wait based on predicted readiness (for planning, before actual is known).
     *
     * @param order the order
     * @param estimatedArrivalTime when the driver is expected to arrive at pickup
     * @return estimated wait minutes (0 if driver arrives after predicted ready)
     */
    public double estimateWaitFromPredicted(Order order, Instant estimatedArrivalTime) {
        if (order.getPredictedReadyAt() == null) return 0;
        if (!estimatedArrivalTime.isBefore(order.getPredictedReadyAt())) return 0;

        long diffSeconds = java.time.Duration.between(estimatedArrivalTime, order.getPredictedReadyAt()).getSeconds();
        // Add expected delay mean for safety margin
        return (diffSeconds / 60.0) + MEAN_DELAY_MINUTES * 0.5;
    }
}
