package com.routechain.v2.executor;

import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.PickupAnchor;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;

record ResolvedSelectedProposal(
        SelectedProposal selectedProposal,
        SelectorCandidate selectorCandidate,
        RouteProposal routeProposal,
        BundleCandidate bundleCandidate,
        PickupAnchor pickupAnchor,
        DriverCandidate driverCandidate) {
}
