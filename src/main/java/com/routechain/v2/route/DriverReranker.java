package com.routechain.v2.route;

import java.util.Comparator;
import java.util.List;

public final class DriverReranker {

    public List<DriverCandidate> rerank(PickupAnchor pickupAnchor, List<DriverRouteFeatures> shortlistedFeatures) {
        List<DriverCandidate> ranked = shortlistedFeatures.stream()
                .map(features -> candidate(pickupAnchor, features))
                .sorted(Comparator.comparingDouble(DriverCandidate::rerankScore).reversed()
                        .thenComparingDouble(DriverCandidate::pickupEtaMinutes)
                        .thenComparing(DriverCandidate::driverId))
                .toList();
        java.util.ArrayList<DriverCandidate> withRanks = new java.util.ArrayList<>();
        int rank = 1;
        for (DriverCandidate candidate : ranked) {
            withRanks.add(new DriverCandidate(
                    candidate.schemaVersion(),
                    candidate.bundleId(),
                    candidate.anchorOrderId(),
                    candidate.driverId(),
                    rank++,
                    candidate.pickupEtaMinutes(),
                    candidate.driverFitScore(),
                    candidate.rerankScore(),
                    candidate.reasons(),
                    candidate.degradeReasons()));
        }
        return List.copyOf(withRanks);
    }

    private DriverCandidate candidate(PickupAnchor pickupAnchor, DriverRouteFeatures features) {
        double rerankScore = Math.max(0.0, Math.min(1.0,
                0.40 * features.driverFitScore()
                        + 0.20 * features.bundleScore()
                        + 0.15 * features.anchorScore()
                        + 0.10 * features.bundleSupportScore()
                        + 0.10 * features.urgencyLift()
                        + 0.05 * features.corridorAffinity()
                        - features.boundaryPenalty()));
        return new DriverCandidate(
                "driver-candidate/v1",
                pickupAnchor.bundleId(),
                pickupAnchor.anchorOrderId(),
                features.driverId(),
                0,
                features.pickupEtaMinutes(),
                features.driverFitScore(),
                rerankScore,
                features.reasons(),
                features.degradeReasons());
    }
}
