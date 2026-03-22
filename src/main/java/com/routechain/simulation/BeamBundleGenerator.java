package com.routechain.simulation;

import com.routechain.domain.*;

import java.util.*;

/**
 * Layer 3 — Beam Search Bundle Generator.
 *
 * Max bundle size = 5 but effective size is dynamic.
 * Uses seed orders + beam search + constrained insertion:
 *
 * 1. Pick seed orders (high priority, long wait, hot zones)
 * 2. Find compatible orders (pickup proximity, direction, SLA)
 * 3. Beam-expand bundles incrementally: [o1] → [o1,o2] → [o1,o2,o3] ...
 * 4. Only keep top B bundles per depth level
 */
public class BeamBundleGenerator {

    private static final int MAX_BUNDLE_SIZE = 5;
    private static final int BEAM_WIDTH = 8;
    private static final double PICKUP_PROXIMITY_M = 2000.0;
    private static final double DROPOFF_PROXIMITY_M = 4000.0;
    private static final double DIRECTION_SIMILARITY_THRESHOLD = 0.3;

    private final double trafficIntensity;
    private final Enums.WeatherProfile weather;
    private final List<Region> zones;

    public BeamBundleGenerator(double trafficIntensity, Enums.WeatherProfile weather,
                               List<Region> zones) {
        this.trafficIntensity = trafficIntensity;
        this.weather = weather;
        this.zones = zones;
    }

    /**
     * Generate bundle candidates using beam search.
     * Returns scored bundles sorted by upper-bound score.
     */
    public List<DispatchPlan.Bundle> generate(List<Order> openOrders) {
        if (openOrders.isEmpty()) return List.of();

        List<Order> seeds = pickSeedOrders(openOrders);
        Set<String> usedInBundle = new HashSet<>();
        List<DispatchPlan.Bundle> allBundles = new ArrayList<>();

        for (Order seed : seeds) {
            if (usedInBundle.contains(seed.getId())) continue;

            List<DispatchPlan.Bundle> beamBundles = beamSearch(seed, openOrders, usedInBundle);
            for (DispatchPlan.Bundle bundle : beamBundles) {
                allBundles.add(bundle);
                // Mark orders as used to avoid overlap
                for (Order o : bundle.orders()) {
                    usedInBundle.add(o.getId());
                }
                break; // take best bundle per seed
            }
        }

        // Also include single-order "bundles" for remaining orders
        for (Order order : openOrders) {
            if (!usedInBundle.contains(order.getId())) {
                allBundles.add(new DispatchPlan.Bundle(
                        "SGL-" + order.getId(),
                        List.of(order), 0, 1));
            }
        }

        return allBundles;
    }

    // ── Seed selection ──────────────────────────────────────────────────

    private List<Order> pickSeedOrders(List<Order> orders) {
        // Priority: high priority first, then longest wait, then hot zone
        return orders.stream()
                .sorted(Comparator
                        .<Order, Integer>comparing(Order::getPriority)
                        .reversed()
                        .thenComparing(Order::getCreatedAt))
                .toList();
    }

    // ── Beam search ─────────────────────────────────────────────────────

    private List<DispatchPlan.Bundle> beamSearch(Order seed, List<Order> allOrders,
                                                  Set<String> alreadyUsed) {
        // Dynamic max size based on conditions
        int dynamicMaxSize = computeDynamicMaxSize();

        // Initial beam: just the seed
        List<List<Order>> beam = new ArrayList<>();
        beam.add(List.of(seed));

        List<DispatchPlan.Bundle> bestBundles = new ArrayList<>();

        for (int depth = 2; depth <= dynamicMaxSize; depth++) {
            List<ScoredBundle> nextBeam = new ArrayList<>();

            for (List<Order> partialBundle : beam) {
                Order lastAdded = partialBundle.get(partialBundle.size() - 1);
                List<Order> compatible = findCompatibleOrders(partialBundle, allOrders, alreadyUsed);

                for (Order candidate : compatible) {
                    List<Order> newBundle = new ArrayList<>(partialBundle);
                    newBundle.add(candidate);

                    if (passesHardBundleConstraints(newBundle)) {
                        double upperBound = quickBundleUpperBound(newBundle);
                        nextBeam.add(new ScoredBundle(newBundle, upperBound));
                    }
                }
            }

            if (nextBeam.isEmpty()) break;

            // Keep top-B bundles
            nextBeam.sort(Comparator.comparingDouble(ScoredBundle::score).reversed());
            beam = nextBeam.stream()
                    .limit(BEAM_WIDTH)
                    .map(ScoredBundle::orders)
                    .toList();

            // Record best bundles at this depth
            for (int i = 0; i < Math.min(3, nextBeam.size()); i++) {
                ScoredBundle sb = nextBeam.get(i);
                bestBundles.add(new DispatchPlan.Bundle(
                        "BDL-" + UUID.randomUUID().toString().substring(0, 6),
                        sb.orders(),
                        sb.score(),
                        sb.orders().size()));
            }
        }

        // Add single-order bundle as fallback
        bestBundles.add(0, new DispatchPlan.Bundle(
                "SGL-" + seed.getId(), List.of(seed), 0, 1));

        // Sort best-first
        bestBundles.sort(Comparator.comparingDouble(DispatchPlan.Bundle::gain).reversed());
        return bestBundles;
    }

    // ── Compatibility checks ────────────────────────────────────────────

    private List<Order> findCompatibleOrders(List<Order> partialBundle,
                                              List<Order> allOrders, Set<String> alreadyUsed) {
        Set<String> bundleIds = new HashSet<>();
        for (Order o : partialBundle) bundleIds.add(o.getId());

        GeoPoint avgPickup = computeAveragePoint(partialBundle, true);
        GeoPoint avgDropoff = computeAveragePoint(partialBundle, false);

        List<Order> compatible = new ArrayList<>();
        for (Order candidate : allOrders) {
            if (bundleIds.contains(candidate.getId())) continue;
            if (alreadyUsed.contains(candidate.getId())) continue;

            // Pickup proximity to bundle center
            double pickupDist = candidate.getPickupPoint().distanceTo(avgPickup);
            if (pickupDist > PICKUP_PROXIMITY_M) continue;

            // Dropoff direction similarity
            double dropoffDist = candidate.getDropoffPoint().distanceTo(avgDropoff);
            if (dropoffDist > DROPOFF_PROXIMITY_M) continue;

            // Direction check: dropoff should be in similar direction from pickup center
            double dirSim = computeDirectionSimilarity(avgPickup, avgDropoff,
                    candidate.getPickupPoint(), candidate.getDropoffPoint());
            if (dirSim < DIRECTION_SIMILARITY_THRESHOLD) continue;

            compatible.add(candidate);
        }

        // Sort by closest pickup first
        compatible.sort(Comparator.comparingDouble(
                o -> o.getPickupPoint().distanceTo(avgPickup)));

        return compatible.subList(0, Math.min(compatible.size(), 10)); // limit candidates
    }

    // ── Hard bundle constraints ─────────────────────────────────────────

    private boolean passesHardBundleConstraints(List<Order> bundle) {
        if (bundle.size() > MAX_BUNDLE_SIZE) return false;

        // All pickups must happen before any dropoff is overdue
        double estimatedRouteMinutes = estimateRouteTime(bundle);
        for (Order o : bundle) {
            if (estimatedRouteMinutes > o.getPromisedEtaMinutes() * 0.9) return false;
        }

        // Check for pickup window conflicts
        // In simulation, all pickups are "ready now", so no conflict

        return true;
    }

    // ── Scoring helpers ─────────────────────────────────────────────────

    private double quickBundleUpperBound(List<Order> bundle) {
        double totalFee = 0;
        double totalStandaloneDist = 0;

        for (Order o : bundle) {
            totalFee += o.getQuotedFee();
            totalStandaloneDist += o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0;
        }

        double bundleDist = estimateBundleRouteDistKm(bundle);
        double efficiencyGain = totalStandaloneDist > 0
                ? Math.max(0, (totalStandaloneDist - bundleDist) / totalStandaloneDist) : 0;

        // Continuation value of end zone
        GeoPoint lastDropoff = bundle.get(bundle.size() - 1).getDropoffPoint();
        double contValue = computeZoneValue(lastDropoff);

        return totalFee * (1 + efficiencyGain * 0.5) + contValue * 10000;
    }

    private int computeDynamicMaxSize() {
        // Dynamic: reduce max size under bad conditions
        if (weather == Enums.WeatherProfile.STORM) return 2;
        if (weather == Enums.WeatherProfile.HEAVY_RAIN && trafficIntensity > 0.6) return 3;
        if (trafficIntensity > 0.7) return 3;
        if (trafficIntensity > 0.5) return 4;
        return MAX_BUNDLE_SIZE;
    }

    // ── Geometry helpers ────────────────────────────────────────────────

    private GeoPoint computeAveragePoint(List<Order> orders, boolean pickup) {
        double lat = 0, lon = 0;
        for (Order o : orders) {
            GeoPoint p = pickup ? o.getPickupPoint() : o.getDropoffPoint();
            lat += p.lat();
            lon += p.lng();
        }
        return new GeoPoint(lat / orders.size(), lon / orders.size());
    }

    private double computeDirectionSimilarity(GeoPoint fromA, GeoPoint toA,
                                               GeoPoint fromB, GeoPoint toB) {
        double dx1 = toA.lng() - fromA.lng();
        double dy1 = toA.lat() - fromA.lat();
        double dx2 = toB.lng() - fromB.lng();
        double dy2 = toB.lat() - fromB.lat();

        double dot = dx1 * dx2 + dy1 * dy2;
        double mag1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
        double mag2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);

        if (mag1 < 1e-9 || mag2 < 1e-9) return 0.5;
        return (dot / (mag1 * mag2) + 1.0) / 2.0; // normalize to [0, 1]
    }

    private double estimateRouteTime(List<Order> bundle) {
        double speedKmh = 25.0 * (1.0 - trafficIntensity * 0.5);
        if (weather == Enums.WeatherProfile.HEAVY_RAIN) speedKmh *= 0.7;
        if (weather == Enums.WeatherProfile.STORM) speedKmh *= 0.4;
        speedKmh = Math.max(8.0, speedKmh);

        double distKm = estimateBundleRouteDistKm(bundle);
        return (distKm / speedKmh) * 60.0;
    }

    private double estimateBundleRouteDistKm(List<Order> bundle) {
        if (bundle.size() == 1) {
            return bundle.get(0).getPickupPoint().distanceTo(bundle.get(0).getDropoffPoint()) / 1000.0;
        }

        double dist = 0;
        // Pickup sequence
        for (int i = 0; i < bundle.size() - 1; i++) {
            dist += bundle.get(i).getPickupPoint()
                    .distanceTo(bundle.get(i + 1).getPickupPoint()) / 1000.0;
        }
        // Last pickup to first dropoff
        dist += bundle.get(bundle.size() - 1).getPickupPoint()
                .distanceTo(bundle.get(0).getDropoffPoint()) / 1000.0;
        // Dropoff sequence
        for (int i = 0; i < bundle.size() - 1; i++) {
            dist += bundle.get(i).getDropoffPoint()
                    .distanceTo(bundle.get(i + 1).getDropoffPoint()) / 1000.0;
        }
        return dist;
    }

    private double computeZoneValue(GeoPoint point) {
        for (Region zone : zones) {
            if (zone.contains(point)) {
                return zone.getOpportunityScore();
            }
        }
        return 0.1;
    }

    private record ScoredBundle(List<Order> orders, double score) {}
}
