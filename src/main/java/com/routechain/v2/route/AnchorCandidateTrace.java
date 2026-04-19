package com.routechain.v2.route;

record AnchorCandidateTrace(
        PickupAnchor anchor,
        boolean retained,
        String rejectReason) {
}
