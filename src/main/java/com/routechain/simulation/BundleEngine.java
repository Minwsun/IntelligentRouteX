package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * Bundle engine — identifies orders that can be grouped together
 * for a single driver pickup-delivery sequence.
 *
 * Bundle criteria:
 * - Pickup points within proximity threshold
 * - Dropoff points in compatible direction
 * - Combined route does not violate SLA of any order
 */
public class BundleEngine {

    private static final double PICKUP_PROXIMITY_METERS = 1500.0;
    private static final double DROPOFF_PROXIMITY_METERS = 3000.0;
    private static final int MAX_BUNDLE_SIZE = 3;
    private static final double SLA_BUFFER_FACTOR = 0.8; // use 80% of SLA as max

    private final double trafficIntensity;
    private final WeatherProfile weatherProfile;

    public BundleEngine(double trafficIntensity, WeatherProfile weatherProfile) {
        this.trafficIntensity = trafficIntensity;
        this.weatherProfile = weatherProfile;
    }

    /**
     * Identify bundleable order pairs from pending orders.
     * Returns list of BundleCandidate, each containing 2-3 orders.
     */
    public List<BundleCandidate> findBundleCandidates(List<Order> pendingOrders) {
        List<BundleCandidate> bundles = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (int i = 0; i < pendingOrders.size(); i++) {
            if (used.contains(pendingOrders.get(i).getId())) continue;
            Order primary = pendingOrders.get(i);

            List<Order> bundleOrders = new ArrayList<>();
            bundleOrders.add(primary);

            for (int j = i + 1; j < pendingOrders.size() && bundleOrders.size() < MAX_BUNDLE_SIZE; j++) {
                if (used.contains(pendingOrders.get(j).getId())) continue;
                Order candidate = pendingOrders.get(j);

                if (isBundleCompatible(primary, candidate)) {
                    bundleOrders.add(candidate);
                }
            }

            if (bundleOrders.size() >= 2) {
                String bundleId = "BDL-" + UUID.randomUUID().toString().substring(0, 6);
                double gain = computeBundleGain(bundleOrders);
                bundles.add(new BundleCandidate(bundleId, bundleOrders, gain));
                for (Order o : bundleOrders) {
                    used.add(o.getId());
                }
            }
        }

        return bundles;
    }

    /**
     * Check if two orders can be bundled together.
     */
    private boolean isBundleCompatible(Order a, Order b) {
        // 1. Pickup proximity
        double pickupDist = a.getPickupPoint().distanceTo(b.getPickupPoint());
        if (pickupDist > PICKUP_PROXIMITY_METERS) return false;

        // 2. Dropoff proximity (loose — same general direction)
        double dropoffDist = a.getDropoffPoint().distanceTo(b.getDropoffPoint());
        if (dropoffDist > DROPOFF_PROXIMITY_METERS) return false;

        // 3. SLA feasibility: combined route time must not exceed min SLA
        double combinedMinutes = estimateCombinedRouteMinutes(a, b);
        double minSla = Math.min(a.getPromisedEtaMinutes(), b.getPromisedEtaMinutes());
        if (combinedMinutes > minSla * SLA_BUFFER_FACTOR) return false;

        return true;
    }

    /**
     * Estimate combined pickup-delivery time for bundled orders.
     */
    private double estimateCombinedRouteMinutes(Order a, Order b) {
        double speedKmh = 25.0 * (1.0 - trafficIntensity * 0.5);
        if (weatherProfile == WeatherProfile.HEAVY_RAIN) speedKmh *= 0.7;
        if (weatherProfile == WeatherProfile.STORM) speedKmh *= 0.4;
        speedKmh = Math.max(8.0, speedKmh);

        // Route: pickup_a -> pickup_b -> dropoff_a -> dropoff_b (nearest-sequence)
        double seg1 = a.getPickupPoint().distanceTo(b.getPickupPoint()) / 1000.0;
        double seg2 = b.getPickupPoint().distanceTo(a.getDropoffPoint()) / 1000.0;
        double seg3 = a.getDropoffPoint().distanceTo(b.getDropoffPoint()) / 1000.0;
        double totalKm = seg1 + seg2 + seg3;

        return (totalKm / speedKmh) * 60.0;
    }

    /**
     * Compute bundle gain: how much distance is saved vs delivering separately.
     */
    private double computeBundleGain(List<Order> orders) {
        if (orders.size() < 2) return 0.0;

        // Separate delivery total distance
        double separateKm = 0;
        for (Order o : orders) {
            separateKm += o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0;
        }

        // Bundle distance (sequential)
        double bundleKm = 0;
        for (int i = 0; i < orders.size() - 1; i++) {
            bundleKm += orders.get(i).getPickupPoint()
                    .distanceTo(orders.get(i + 1).getPickupPoint()) / 1000.0;
        }
        // Add last pickup to first dropoff
        bundleKm += orders.get(orders.size() - 1).getPickupPoint()
                .distanceTo(orders.get(0).getDropoffPoint()) / 1000.0;
        for (int i = 0; i < orders.size() - 1; i++) {
            bundleKm += orders.get(i).getDropoffPoint()
                    .distanceTo(orders.get(i + 1).getDropoffPoint()) / 1000.0;
        }

        // Gain: percentage of distance saved
        if (separateKm < 0.01) return 0.0;
        return Math.max(0, (separateKm - bundleKm) / separateKm);
    }

    /**
     * Data class for a bundle candidate.
     */
    public static class BundleCandidate {
        private final String bundleId;
        private final List<Order> orders;
        private final double gain;

        public BundleCandidate(String bundleId, List<Order> orders, double gain) {
            this.bundleId = bundleId;
            this.orders = List.copyOf(orders);
            this.gain = gain;
        }

        public String getBundleId() { return bundleId; }
        public List<Order> getOrders() { return orders; }
        public double getGain() { return gain; }
        public int getSize() { return orders.size(); }
    }
}
