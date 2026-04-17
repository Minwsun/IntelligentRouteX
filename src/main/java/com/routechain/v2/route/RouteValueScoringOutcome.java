package com.routechain.v2.route;

import com.routechain.v2.MlStageMetadata;

import java.util.List;

record RouteValueScoringOutcome(
        RouteProposalCandidate candidate,
        List<String> degradeReasons,
        List<MlStageMetadata> mlStageMetadata) {
}
