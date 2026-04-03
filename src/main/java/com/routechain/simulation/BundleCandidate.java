package com.routechain.simulation;

import com.routechain.domain.Order;

import java.util.List;

/**
 * Candidate bundle constructed before the final dispatch plan is built.
 */
public record BundleCandidate(
        String candidateId,
        String driverId,
        String serviceTier,
        List<Order> orders,
        double estimatedRouteCost,
        double compatibilityScore,
        String backend,
        String rationale
) {
    public BundleCandidate {
        candidateId = candidateId == null || candidateId.isBlank() ? "bundle-candidate-unknown" : candidateId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        orders = orders == null ? List.of() : List.copyOf(orders);
        backend = backend == null || backend.isBlank() ? "heuristic" : backend;
        rationale = rationale == null ? "" : rationale;
    }
}
