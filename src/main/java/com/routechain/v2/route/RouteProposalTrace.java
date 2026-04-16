package com.routechain.v2.route;

import java.util.List;

record RouteProposalTrace(
        RouteProposalTupleKey tupleKey,
        RouteProposalSource source,
        String stopOrderSignature,
        double driverRerankContribution,
        double bundleContribution,
        double anchorContribution,
        double pickupEtaContribution,
        double completionEtaContribution,
        double supportContribution,
        double urgencyLift,
        double boundaryPenalty,
        double fallbackPenalty,
        List<String> validationReasons) {
}
