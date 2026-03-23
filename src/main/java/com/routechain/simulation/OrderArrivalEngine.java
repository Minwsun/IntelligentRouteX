package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * OrderArrivalEngine — owns demand intensity calculation.
 *
 * Formula:
 *   lambda(z,t) = baseRate(z) * hourProfile(z,t) * weatherFactor(z,t)
 *               * shockMultiplier(z,t) * selfExciteFactor(z,t)
 *               * neighborSpill(z,t) * manualDemandMultiplier
 *
 * Actual count: orders(z,t) ~ NegBin(mean=lambda, dispersion=k)
 *
 * Self-excitation (Hawkes-lite):
 *   Recent orders in zone boost lambda temporarily.
 *
 * Neighbor spill:
 *   Adjacent zone burst → small spillover to this zone.
 */
public class OrderArrivalEngine {

    /** Dispersion parameter for NegBin (smaller = more bursty). */
    private static final double NEGBIN_DISPERSION = 3.0;

    /** Self-excitation decay factor per tick. */
    private static final double SELF_EXCITE_DECAY = 0.85;

    /** Self-excitation boost per order created. */
    private static final double SELF_EXCITE_BOOST_PER_ORDER = 0.03;

    /** Max self-excitation multiplier. */
    private static final double MAX_SELF_EXCITE = 1.8;

    /** Neighbor spill fraction. */
    private static final double NEIGHBOR_SPILL_FRACTION = 0.08;

    /** Max lambda cap to prevent system overload. */
    private static final double MAX_LAMBDA = 12.0;

    private final List<Region> regions;
    private final ScenarioShockEngine shockEngine;
    private final Random rng;
    private final Map<String, Double> baseDemandRates;

    /** Per-zone self-excitation state (decays each tick). */
    private final double[] selfExciteState;

    /** Per-zone recent order count (for spill calculation). */
    private final int[] recentOrderCounts;

    private double manualDemandMultiplier = 1.0;

    public OrderArrivalEngine(List<Region> regions, ScenarioShockEngine shockEngine, Random rng) {
        this.regions = regions;
        this.shockEngine = shockEngine;
        this.rng = rng;
        this.selfExciteState = new double[regions.size()];
        this.recentOrderCounts = new int[regions.size()];
        this.baseDemandRates = HcmcCityData.baseDemandRates();
    }

    /**
     * Generate orders for all zones at current decision tick.
     *
     * @param simulatedHour current hour (0-23)
     * @param weather       current weather profile
     * @param tick          current tick counter
     * @return list of newly created orders
     */
    public List<Order> generateOrders(int simulatedHour, WeatherProfile weather, long tick) {
        List<Order> newOrders = new ArrayList<>();

        // Phase 1: compute lambda per zone
        double[] lambdas = new double[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            lambdas[i] = computeLambda(i, simulatedHour, weather, tick);
        }

        // Phase 2: neighbor spill (use lambdas as input)
        double[] spillBoosts = computeNeighborSpill(lambdas);

        // Phase 3: generate orders per zone
        for (int i = 0; i < regions.size(); i++) {
            double finalLambda = Math.min(MAX_LAMBDA, lambdas[i] + spillBoosts[i]);
            if (finalLambda <= 0.01) continue;

            int count = SimulationMath.nextNegativeBinomial(finalLambda, NEGBIN_DISPERSION, rng);
            count = Math.min(count, 8); // hard cap per zone per tick

            for (int j = 0; j < count; j++) {
                Order order = createOrder(regions.get(i), simulatedHour);
                if (order != null) {
                    newOrders.add(order);
                }
            }

            // Update self-excitation state
            selfExciteState[i] = selfExciteState[i] * SELF_EXCITE_DECAY
                    + count * SELF_EXCITE_BOOST_PER_ORDER;
            selfExciteState[i] = Math.min(selfExciteState[i], MAX_SELF_EXCITE - 1.0);
            recentOrderCounts[i] = count;
        }

        return newOrders;
    }

    /**
     * Compute demand intensity lambda for a specific zone.
     */
    private double computeLambda(int zoneIndex, int simulatedHour,
                                  WeatherProfile weather, long tick) {
        Region region = regions.get(zoneIndex);

        // Base rate from city data
        double baseRate = baseDemandRates.getOrDefault(region.getId(), 0.15);

        // Hour profile
        double hourMultiplier = HcmcCityData.hourlyMultiplier(simulatedHour);

        // Weather factor
        double weatherFactor = computeWeatherFactor(weather, region);

        // Shock multiplier (from ScenarioShockEngine)
        String regionType = region.getZoneType() != null ? region.getZoneType().name() : "MIXED";
        double shockMultiplier = shockEngine.getDemandMultiplier(region.getId(), regionType, tick);

        // Self-excitation (Hawkes-lite)
        double selfExciteFactor = 1.0 + selfExciteState[zoneIndex];

        // Manual multiplier
        double lambda = baseRate * hourMultiplier * weatherFactor
                * shockMultiplier * selfExciteFactor * manualDemandMultiplier;

        return lambda;
    }

    /**
     * Compute neighbor spill: if adjacent zones have high recent production,
     * spill a fraction to this zone.
     */
    private double[] computeNeighborSpill(double[] lambdas) {
        double[] spill = new double[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            double neighborSum = 0;
            int neighborCount = 0;
            for (int j = 0; j < regions.size(); j++) {
                if (i == j) continue;
                double dist = regions.get(i).getCenter().distanceTo(regions.get(j).getCenter());
                if (dist < 3000) { // 3km adjacency
                    neighborSum += recentOrderCounts[j];
                    neighborCount++;
                }
            }
            if (neighborCount > 0) {
                spill[i] = (neighborSum / neighborCount) * NEIGHBOR_SPILL_FRACTION;
            }
        }
        return spill;
    }

    /**
     * Weather effect on demand: rain increases food/apartment demand,
     * storm suppresses some categories.
     */
    private double computeWeatherFactor(WeatherProfile weather, Region region) {
        return switch (weather) {
            case CLEAR -> 1.0;
            case LIGHT_RAIN -> 1.15;
            case HEAVY_RAIN -> region.getZoneType() == Region.ZoneType.RESIDENTIAL ? 1.4 : 1.15;
            case STORM -> 0.7;
        };
    }

    /**
     * Create a single order within a region.
     */
    private Order createOrder(Region region, int simulatedHour) {
        GeoPoint pickup = randomPointInRegion(region);
        if (pickup == null) return null;

        // Dropoff: pick a different region or same region with offset
        Region dropRegion = pickDropoffRegion(region);
        GeoPoint dropoff = randomPointInRegion(dropRegion);
        if (dropoff == null) return null;

        // Fee: distance-based + surge
        double distKm = pickup.distanceTo(dropoff) / 1000.0;
        double baseFee = 12000 + distKm * 5000;
        double surge = region.getSurgeSeverity() == com.routechain.domain.Enums.SurgeSeverity.HIGH ? 1.5
                : region.getSurgeSeverity() == com.routechain.domain.Enums.SurgeSeverity.MEDIUM ? 1.2 : 1.0;
        double fee = baseFee * surge;

        // ETA promise
        int etaPromise = Math.max(15, (int) (distKm * 4) + 10);

        String orderId = "ORD-" + System.nanoTime() + "-" + rng.nextInt(1000);
        String custId = "CUST-" + rng.nextInt(10000);

        return new Order(orderId, custId, region.getId(),
                pickup, dropoff, dropRegion.getId(), fee, etaPromise);
    }

    private GeoPoint randomPointInRegion(Region region) {
        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = Math.sqrt(rng.nextDouble()) * region.getRadiusMeters();
        double dLat = dist * Math.cos(angle) / 111320.0;
        double dLng = dist * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(region.getCenter().lat())));
        GeoPoint point = new GeoPoint(region.getCenter().lat() + dLat, region.getCenter().lng() + dLng);

        // Basic land check: must be near a region center
        for (Region r : regions) {
            if (r.contains(point)) return point;
        }
        return region.getCenter(); // fallback to center
    }

    private Region pickDropoffRegion(Region origin) {
        // 60% same region, 40% random other region
        if (rng.nextDouble() < 0.6 || regions.size() < 2) {
            return origin;
        }
        Region target;
        do {
            target = regions.get(rng.nextInt(regions.size()));
        } while (target.getId().equals(origin.getId()) && regions.size() > 1);
        return target;
    }

    // ── Setters ────────────────────────────────────────────────────────

    public void setManualDemandMultiplier(double multiplier) {
        this.manualDemandMultiplier = multiplier;
    }

    public double getManualDemandMultiplier() {
        return manualDemandMultiplier;
    }

    /** Reset state. */
    public void reset() {
        java.util.Arrays.fill(selfExciteState, 0);
        java.util.Arrays.fill(recentOrderCounts, 0);
        manualDemandMultiplier = 1.0;
    }
}
