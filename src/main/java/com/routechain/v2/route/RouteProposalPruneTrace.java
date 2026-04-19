package com.routechain.v2.route;

record RouteProposalPruneTrace(
        RouteProposalCandidate candidate,
        boolean retained,
        String pruneReason) {
}
