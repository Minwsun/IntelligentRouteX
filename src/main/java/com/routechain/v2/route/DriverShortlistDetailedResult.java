package com.routechain.v2.route;

import com.routechain.v2.MlStageMetadata;

import java.util.List;

record DriverShortlistDetailedResult(
        List<DriverRouteFeatures> shortlistedFeatures,
        List<DriverShortlistCandidateTrace> candidateTraces,
        List<String> degradeReasons,
        List<MlStageMetadata> mlStageMetadata) {

    DriverShortlistResult toDriverShortlistResult() {
        return new DriverShortlistResult(shortlistedFeatures, degradeReasons, mlStageMetadata);
    }
}
