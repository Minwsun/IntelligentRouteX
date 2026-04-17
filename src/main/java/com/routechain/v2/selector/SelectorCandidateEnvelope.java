package com.routechain.v2.selector;

record SelectorCandidateEnvelope(
        SelectorCandidate candidate,
        double projectedPickupEtaMinutes) {
}
