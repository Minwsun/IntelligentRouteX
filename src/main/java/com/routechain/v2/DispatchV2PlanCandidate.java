package com.routechain.v2;

import com.routechain.simulation.DispatchPlan;

public record DispatchV2PlanCandidate(
        String candidateId,
        DispatchPlan plan,
        BundleScore bundleScore,
        RobustUtility robustUtility,
        GlobalValue globalValue,
        String summary) {
}
