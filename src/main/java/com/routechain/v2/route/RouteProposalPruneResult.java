package com.routechain.v2.route;

import java.util.List;

record RouteProposalPruneResult(
        List<RouteProposalCandidate> retainedCandidates,
        List<RouteProposalPruneTrace> traces) {
}
