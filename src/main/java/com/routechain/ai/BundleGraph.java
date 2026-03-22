package com.routechain.ai;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.DispatchPlan;

import java.util.*;

/**
 * Bundle Graph — order compatibility graph with weighted edges.
 * Replaces simple beam search with graph-based bundle discovery.
 *
 * Structure:
 * - Node = pending order
 * - Edge = bundle compatibility (weighted by synergy score)
 * - Bundle search = beam search on graph (max-weight clique approximation)
 *
 * Edge synergy(i,j) =
 *   0.30 * pickupCloseness
 * + 0.25 * directionSimilarity
 * + 0.15 * etaSlackCompatibility
 * + 0.10 * districtTransitionGain
 * + 0.10 * (1 - weatherRisk)
 * - 0.10 * pickupConflictPenalty
 */
public class BundleGraph {

    private static final double EDGE_THRESHOLD = 0.15;  // min synergy to create edge
    private static final double MAX_PICKUP_DIST_M = 2500;
    private static final double MAX_DROPOFF_DIST_M = 5000;
    private static final int BEAM_WIDTH = 8;

    private final double[][] adjacency;
    private final List<Order> orders;
    private final int n;

    private final double trafficIntensity;
    private final WeatherProfile weather;
    private final SpatiotemporalField field;

    /**
     * Build the bundle compatibility graph.
     */
    public BundleGraph(List<Order> pendingOrders, SpatiotemporalField field,
                       double trafficIntensity, WeatherProfile weather) {
        this.orders = new ArrayList<>(pendingOrders);
        this.n = orders.size();
        this.adjacency = new double[n][n];
        this.field = field;
        this.trafficIntensity = trafficIntensity;
        this.weather = weather;

        buildEdges();
    }

    // ── Edge construction ───────────────────────────────────────────────

    private void buildEdges() {
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double synergy = computeSynergy(orders.get(i), orders.get(j));
                if (synergy > EDGE_THRESHOLD) {
                    adjacency[i][j] = synergy;
                    adjacency[j][i] = synergy;
                }
            }
        }
    }

    private double computeSynergy(Order a, Order b) {
        // 1. Pickup closeness [0, 1]
        double pickupDist = a.getPickupPoint().distanceTo(b.getPickupPoint());
        if (pickupDist > MAX_PICKUP_DIST_M) return 0; // too far, no edge
        double pickupCloseness = 1.0 - (pickupDist / MAX_PICKUP_DIST_M);

        // 2. Dropoff direction similarity [0, 1]
        GeoPoint pickupCenter = new GeoPoint(
                (a.getPickupPoint().lat() + b.getPickupPoint().lat()) / 2,
                (a.getPickupPoint().lng() + b.getPickupPoint().lng()) / 2);
        double dirSim = computeDirectionSimilarity(
                pickupCenter, a.getDropoffPoint(), pickupCenter, b.getDropoffPoint());

        // 3. ETA slack compatibility [0, 1]
        double minSlack = Math.min(a.getPromisedEtaMinutes(), b.getPromisedEtaMinutes());
        double slackCompat = Math.min(1.0, minSlack / 45.0);

        // 4. District transition gain [0, 1]
        double dropoffDist = a.getDropoffPoint().distanceTo(b.getDropoffPoint());
        double districtGain = dropoffDist < MAX_DROPOFF_DIST_M ? 0.8 : 0.3;
        if (a.getDropoffRegionId() != null && a.getDropoffRegionId().equals(b.getDropoffRegionId())) {
            districtGain = 1.0;
        }

        // 5. Weather risk [0, 1] - shared risk
        double weatherRisk = switch (weather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.15;
            case HEAVY_RAIN -> 0.35;
            case STORM -> 0.6;
        };

        // 6. Pickup conflict penalty [0, 1]
        double pickupConflict = 0;
        // Orders from different regions have higher conflict
        if (a.getPickupRegionId() != null && !a.getPickupRegionId().equals(b.getPickupRegionId())) {
            pickupConflict = 0.3;
        }

        return 0.30 * pickupCloseness
             + 0.25 * dirSim
             + 0.15 * slackCompat
             + 0.10 * districtGain
             + 0.10 * (1.0 - weatherRisk)
             - 0.10 * pickupConflict;
    }

    // ── Bundle search ───────────────────────────────────────────────────

    /**
     * Search for optimal bundles using beam search on the graph.
     * Returns list of bundles sorted by total synergy (descending).
     */
    public List<DispatchPlan.Bundle> searchBundles(int maxBundleSize) {
        int effectiveMaxSize = dynamicMaxSize(maxBundleSize);
        Set<Integer> usedNodes = new HashSet<>();
        List<DispatchPlan.Bundle> allBundles = new ArrayList<>();

        // Sort nodes by degree (most connected first = best seeds)
        List<Integer> seeds = new ArrayList<>();
        for (int i = 0; i < n; i++) seeds.add(i);
        seeds.sort(Comparator.comparingDouble(this::nodeDegree).reversed());

        for (int seed : seeds) {
            if (usedNodes.contains(seed)) continue;

            List<DispatchPlan.Bundle> candidates = beamSearchFromSeed(
                    seed, effectiveMaxSize, usedNodes);

            if (!candidates.isEmpty()) {
                DispatchPlan.Bundle best = candidates.get(0);
                allBundles.add(best);

                // Mark used
                for (Order o : best.orders()) {
                    for (int i = 0; i < n; i++) {
                        if (orders.get(i).getId().equals(o.getId())) {
                            usedNodes.add(i);
                            break;
                        }
                    }
                }
            }
        }

        // Add remaining as singletons
        for (int i = 0; i < n; i++) {
            if (!usedNodes.contains(i)) {
                allBundles.add(new DispatchPlan.Bundle(
                        "SGL-" + orders.get(i).getId(),
                        List.of(orders.get(i)), 0, 1));
            }
        }

        return allBundles;
    }

    private List<DispatchPlan.Bundle> beamSearchFromSeed(
            int seed, int maxSize, Set<Integer> used) {

        List<BeamCandidate> beam = new ArrayList<>();
        beam.add(new BeamCandidate(List.of(seed), 0));

        List<DispatchPlan.Bundle> bestBundles = new ArrayList<>();

        for (int depth = 2; depth <= maxSize; depth++) {
            List<BeamCandidate> nextBeam = new ArrayList<>();

            for (BeamCandidate partial : beam) {
                // Find compatible neighbors
                List<Integer> neighbors = findCompatibleNeighbors(partial.nodes, used);

                for (int neighbor : neighbors) {
                    double addedSynergy = synergyToBundle(neighbor, partial.nodes);
                    if (addedSynergy > EDGE_THRESHOLD) {
                        List<Integer> newNodes = new ArrayList<>(partial.nodes);
                        newNodes.add(neighbor);
                        nextBeam.add(new BeamCandidate(newNodes, partial.score + addedSynergy));
                    }
                }
            }

            if (nextBeam.isEmpty()) break;

            nextBeam.sort(Comparator.comparingDouble(c -> -c.score));
            beam = nextBeam.subList(0, Math.min(nextBeam.size(), BEAM_WIDTH));

            // Record best at this depth
            BeamCandidate top = beam.get(0);
            List<Order> bundleOrders = new ArrayList<>();
            for (int idx : top.nodes) bundleOrders.add(orders.get(idx));

            bestBundles.add(new DispatchPlan.Bundle(
                    "GBL-" + UUID.randomUUID().toString().substring(0, 6),
                    bundleOrders, top.score, bundleOrders.size()));
        }

        // Add single-order bundle as baseline
        bestBundles.add(0, new DispatchPlan.Bundle(
                "SGL-" + orders.get(seed).getId(),
                List.of(orders.get(seed)), 0, 1));

        bestBundles.sort(Comparator.comparingDouble(DispatchPlan.Bundle::gain).reversed());
        return bestBundles;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<Integer> findCompatibleNeighbors(List<Integer> bundle, Set<Integer> used) {
        List<Integer> neighbors = new ArrayList<>();
        Set<Integer> bundleSet = new HashSet<>(bundle);

        for (int i = 0; i < n; i++) {
            if (bundleSet.contains(i) || used.contains(i)) continue;
            // Must have edge to at least one node in bundle
            boolean connected = false;
            for (int b : bundle) {
                if (adjacency[b][i] > EDGE_THRESHOLD) {
                    connected = true;
                    break;
                }
            }
            if (connected) neighbors.add(i);
        }
        return neighbors;
    }

    private double synergyToBundle(int node, List<Integer> bundle) {
        double totalSynergy = 0;
        for (int b : bundle) {
            totalSynergy += adjacency[b][node];
        }
        return totalSynergy / bundle.size(); // average synergy to all bundle members
    }

    private double nodeDegree(int node) {
        double degree = 0;
        for (int j = 0; j < n; j++) {
            if (adjacency[node][j] > 0) degree += adjacency[node][j];
        }
        return degree;
    }

    private int dynamicMaxSize(int maxBundleSize) {
        if (weather == WeatherProfile.STORM) return Math.min(maxBundleSize, 2);
        if (weather == WeatherProfile.HEAVY_RAIN && trafficIntensity > 0.6)
            return Math.min(maxBundleSize, 3);
        if (trafficIntensity > 0.7) return Math.min(maxBundleSize, 3);
        return maxBundleSize;
    }

    private double computeDirectionSimilarity(
            GeoPoint fromA, GeoPoint toA, GeoPoint fromB, GeoPoint toB) {
        double dx1 = toA.lng() - fromA.lng();
        double dy1 = toA.lat() - fromA.lat();
        double dx2 = toB.lng() - fromB.lng();
        double dy2 = toB.lat() - fromB.lat();

        double dot = dx1 * dx2 + dy1 * dy2;
        double mag1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
        double mag2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);

        if (mag1 < 1e-9 || mag2 < 1e-9) return 0.5;
        return (dot / (mag1 * mag2) + 1.0) / 2.0;
    }

    /**
     * Get edge count (number of compatible pairs).
     */
    public int getEdgeCount() {
        int count = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adjacency[i][j] > EDGE_THRESHOLD) count++;
            }
        }
        return count;
    }

    public int getNodeCount() { return n; }

    private record BeamCandidate(List<Integer> nodes, double score) {}
}
