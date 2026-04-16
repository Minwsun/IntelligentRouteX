package com.routechain.v2.route;

record RouteProposalCandidate(
        RouteProposal proposal,
        RouteProposalTupleKey tupleKey,
        PickupAnchor pickupAnchor,
        DriverCandidate driverCandidate,
        RouteProposalTrace trace) {
}
