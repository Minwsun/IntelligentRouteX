package com.routechain.v2.route;

import com.routechain.simulation.DispatchPlan;
import com.routechain.v2.RobustUtility;

public record RouteProposal(
        String routeProposalId,
        String source,
        DispatchPlan plan,
        double preliminaryScore,
        RobustUtility robustUtility) {
}
