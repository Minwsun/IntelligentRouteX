package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;

import java.util.List;
import java.util.Random;

/**
 * DriverMotionEngine — realistic driver movement with state machine.
 *
 * Speed equation:
 *   speed = baseSpeed(driver) * styleFactor * corridorFactor
 *         * intersectionFactor * queueFactor * weatherFactor * loadFactor
 *
 * State machine per driver:
 *   FREE_FLOW → APPROACH_INTERSECTION → QUEUED → FREE_FLOW
 *            → STOP_GO → FREE_FLOW
 *            → MERCHANT_WAIT → FREE_FLOW
 *
 * Micro-hesitation: random pause with context-dependent probability.
 */
public class DriverMotionEngine {

    // ── Intersection detection ──────────────────────────────────────────
    /** Proximity threshold to consider a segment as near intersection (meters). */
    private static final double INTERSECTION_PROXIMITY_METERS = 150.0;

    /** Speed reduction at intersections (30-70% of normal). */
    private static final double INTERSECTION_SPEED_MIN = 0.30;
    private static final double INTERSECTION_SPEED_MAX = 0.70;

    /** Probability of full stop at intersection per sub-tick. */
    private static final double INTERSECTION_STOP_PROBABILITY = 0.25;

    /** Max sub-ticks to wait at intersection (red light). */
    private static final int INTERSECTION_MAX_WAIT_SUBTICKS = 4;

    // ── Queue / stop-go ─────────────────────────────────────────────────
    /** Probability of entering stop-go when traffic exposure is high. */
    private static final double STOP_GO_BASE_PROBABILITY = 0.12;

    /** Stop-go alternation: stop 1-2 sub-ticks, crawl 1-2 sub-ticks. */
    private static final int STOP_GO_MAX_STOP_SUBTICKS = 2;

    // ── Micro-hesitation ────────────────────────────────────────────────
    /** Base probability of micro-hesitation per sub-tick. */
    private static final double MICRO_HESITATION_BASE_PROB = 0.04;

    private final Random rng;
    private final List<GeoPoint> intersectionPoints;

    public DriverMotionEngine(Random rng, List<GeoPoint> intersectionPoints) {
        this.rng = rng;
        this.intersectionPoints = intersectionPoints;
    }

    /**
     * Move a single driver for one sub-tick (5 simulated seconds).
     *
     * @param driver   the driver to move
     * @param weather  current weather
     * @param globalTrafficIntensity global traffic [0..1]
     * @param subTickSeconds seconds per sub-tick (typically 5)
     */
    public void moveDriver(Driver driver, WeatherProfile weather,
                           double globalTrafficIntensity, int subTickSeconds) {
        // Handle delay states first
        if (driver.getMicroDelayTicksRemaining() > 0) {
            driver.setMicroDelayTicksRemaining(driver.getMicroDelayTicksRemaining() - 1);
            driver.setSpeedKmh(0);
            return; // standing still this sub-tick
        }

        if (driver.getQueueTicksRemaining() > 0) {
            driver.setQueueTicksRemaining(driver.getQueueTicksRemaining() - 1);
            // Crawl very slowly during queue
            driver.setSpeedKmh(rng.nextDouble() < 0.5 ? 0 : 3 + rng.nextDouble() * 5);
            if (driver.getSpeedKmh() > 0) {
                crawlDriver(driver, subTickSeconds);
            }
            if (driver.getQueueTicksRemaining() == 0) {
                driver.setMovementMode(Driver.MovementMode.FREE_FLOW);
            }
            return;
        }

        if (driver.getMerchantWaitTicksRemaining() > 0) {
            driver.setMerchantWaitTicksRemaining(driver.getMerchantWaitTicksRemaining() - 1);
            driver.setSpeedKmh(0);
            driver.setMovementMode(Driver.MovementMode.MERCHANT_WAIT);
            if (driver.getMerchantWaitTicksRemaining() == 0) {
                driver.setMovementMode(Driver.MovementMode.FREE_FLOW);
            }
            return;
        }

        // Compute effective speed
        double speedKmh = computeEffectiveSpeed(driver, weather, globalTrafficIntensity);

        // Check for intersection approach
        if (isNearIntersection(driver.getCurrentLocation())) {
            driver.setMovementMode(Driver.MovementMode.APPROACH_INTERSECTION);
            speedKmh *= (INTERSECTION_SPEED_MIN
                    + rng.nextDouble() * (INTERSECTION_SPEED_MAX - INTERSECTION_SPEED_MIN));

            // Random full stop (red light simulation)
            if (rng.nextDouble() < INTERSECTION_STOP_PROBABILITY) {
                int waitTicks = 1 + rng.nextInt(INTERSECTION_MAX_WAIT_SUBTICKS);
                driver.setMicroDelayTicksRemaining(waitTicks);
                driver.setSpeedKmh(0);
                return;
            }
        } else {
            driver.setMovementMode(Driver.MovementMode.FREE_FLOW);
        }

        // Check for queue entry (based on traffic exposure)
        double trafficExposure = driver.getCurrentTrafficExposure();
        double queueProb = STOP_GO_BASE_PROBABILITY * (trafficExposure + globalTrafficIntensity);
        if (rng.nextDouble() < queueProb && trafficExposure > 0.4) {
            int queueTicks = 1 + rng.nextInt(STOP_GO_MAX_STOP_SUBTICKS);
            driver.setQueueTicksRemaining(queueTicks);
            driver.setMovementMode(Driver.MovementMode.STOP_GO);
            driver.setSpeedKmh(0);
            return;
        }

        // Micro-hesitation check
        double hesitationProb = MICRO_HESITATION_BASE_PROB
                + (weather == WeatherProfile.HEAVY_RAIN ? 0.03 : 0)
                + (weather == WeatherProfile.STORM ? 0.06 : 0)
                + globalTrafficIntensity * 0.04;
        if (rng.nextDouble() < hesitationProb) {
            int delayTicks = 1 + rng.nextInt(2);
            driver.setMicroDelayTicksRemaining(delayTicks);
            driver.setSpeedKmh(speedKmh * 0.2); // brief deceleration flash
            return;
        }

        // Normal movement
        driver.setSpeedKmh(speedKmh);
        double speedMps = speedKmh / 3.6;
        double distanceThisTick = speedMps * subTickSeconds;
        advanceDriverAlongRoute(driver, distanceThisTick);
    }

    /**
     * Compute effective speed considering all factors.
     */
    private double computeEffectiveSpeed(Driver driver, WeatherProfile weather,
                                          double globalTraffic) {
        // Base speed from style profile
        double baseKmh = switch (driver.getDriverStyleProfile()) {
            case CAUTIOUS -> 22.0 + rng.nextDouble() * 4;
            case NORMAL -> 28.0 + rng.nextDouble() * 6;
            case AGGRESSIVE -> 35.0 + rng.nextDouble() * 8;
        };

        // Corridor factor (use traffic exposure as proxy)
        double corridorFactor = 1.0 - driver.getCurrentTrafficExposure() * 0.4;

        // Weather factor
        double weatherFactor = switch (weather) {
            case CLEAR -> 1.0;
            case LIGHT_RAIN -> 0.85;
            case HEAVY_RAIN -> 0.65;
            case STORM -> 0.35;
        };

        // Load factor (more orders → more careful)
        int loadCount = driver.getCurrentOrderCount();
        double loadFactor = loadCount > 0 ? 1.0 - (loadCount * 0.05) : 1.0;
        loadFactor = Math.max(0.75, loadFactor);

        // Fatigue factor
        double fatigueFactor = 1.0 - driver.getFatigueLevel() * 0.15;

        double finalSpeed = baseKmh * corridorFactor * weatherFactor * loadFactor * fatigueFactor;
        return Math.max(5.0, Math.min(50.0, finalSpeed)); // clamp 5-50 km/h
    }

    /**
     * Check if driver is near an intersection point.
     */
    private boolean isNearIntersection(GeoPoint pos) {
        for (GeoPoint intersection : intersectionPoints) {
            if (pos.distanceTo(intersection) < INTERSECTION_PROXIMITY_METERS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Advance driver along their route waypoints by given distance.
     */
    private void advanceDriverAlongRoute(Driver driver, double remainingDist) {
        GeoPoint current = driver.getCurrentLocation();
        GeoPoint target = driver.getTargetLocation();

        if (target == null) return;

        if (driver.hasRouteWaypoints()) {
            List<GeoPoint> waypoints = driver.getRouteWaypoints();
            while (remainingDist > 0 && !waypoints.isEmpty()) {
                GeoPoint nextWp = waypoints.get(0);
                double wpDist = current.distanceTo(nextWp);
                if (wpDist <= remainingDist) {
                    remainingDist -= wpDist;
                    current = nextWp;
                    waypoints.remove(0);
                } else {
                    current = current.moveTowards(nextWp, remainingDist);
                    remainingDist = 0;
                }
            }
        } else {
            // Direct movement toward target if no waypoints
            double dist = current.distanceTo(target);
            if (dist <= remainingDist) {
                current = target;
            } else {
                current = current.moveTowards(target, remainingDist);
            }
        }
        driver.setCurrentLocation(current);
    }

    /**
     * Crawl driver very slowly (used during queue state).
     */
    private void crawlDriver(Driver driver, int subTickSeconds) {
        double speedMps = driver.getSpeedKmh() / 3.6;
        double dist = speedMps * subTickSeconds;
        if (dist > 0) {
            advanceDriverAlongRoute(driver, dist);
        }
    }
}
