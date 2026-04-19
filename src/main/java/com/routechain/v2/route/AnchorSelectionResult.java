package com.routechain.v2.route;

import java.util.List;

record AnchorSelectionResult(
        List<PickupAnchor> selectedAnchors,
        List<AnchorCandidateTrace> candidateTraces) {
}
