package com.routechain.v2.route;

record DriverShortlistCandidateTrace(
        DriverRouteFeatures features,
        boolean retained,
        String rejectReason) {
}
