package com.routechain.v2.bundle;

public record BoundaryCandidate(
        String orderId,
        double supportScore,
        boolean boundaryCross,
        boolean urgent,
        String corridorSignature) {
}
