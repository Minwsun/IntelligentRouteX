package com.routechain.v2.selector;

import com.routechain.v2.route.RouteProposalSource;

record SelectorCandidateIdentityKey(
        String bundleId,
        String anchorOrderId,
        String driverId,
        RouteProposalSource source,
        String stopOrderSignature) {
}
