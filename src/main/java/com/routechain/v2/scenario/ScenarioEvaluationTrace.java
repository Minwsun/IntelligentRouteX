package com.routechain.v2.scenario;

import java.util.List;

record ScenarioEvaluationTrace(
        String proposalId,
        ScenarioType scenario,
        double basePickupEtaMinutes,
        double baseCompletionEtaMinutes,
        double baseRouteValue,
        double pickupEtaDeltaMinutes,
        double completionEtaDeltaMinutes,
        double valueDelta,
        double lateRiskDelta,
        double cancelRiskDelta,
        double landingValueDelta,
        double stabilityScoreDelta,
        boolean applied,
        List<String> details) {
}
