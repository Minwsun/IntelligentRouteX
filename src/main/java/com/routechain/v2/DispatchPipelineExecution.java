package com.routechain.v2;

import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.context.DispatchEtaContextStage;
import com.routechain.v2.feedback.HotStartReusePlan;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.scenario.DispatchScenarioStage;

public record DispatchPipelineExecution(
        DispatchV2Result result,
        HotStartReusePlan hotStartReusePlan,
        DispatchEtaContextStage etaStage,
        DispatchPairClusterStage pairClusterStage,
        DispatchBundleStage bundleStage,
        DispatchRouteCandidateStage routeCandidateStage,
        DispatchRouteProposalStage routeProposalStage,
        DispatchScenarioStage scenarioStage) {
}
