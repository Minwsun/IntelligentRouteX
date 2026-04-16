package com.routechain.v2.route;

record DriverRouteFeatures(
        String bundleId,
        String anchorOrderId,
        String driverId,
        double pickupEtaMinutes,
        double etaUncertainty,
        double bundleScore,
        double anchorScore,
        double bundleSupportScore,
        double corridorAffinity,
        double urgencyLift,
        double boundaryPenalty,
        double driverFitScore,
        java.util.List<String> reasons,
        java.util.List<String> degradeReasons) {
}
