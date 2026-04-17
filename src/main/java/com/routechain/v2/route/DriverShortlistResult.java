package com.routechain.v2.route;

import com.routechain.v2.MlStageMetadata;

import java.util.List;

record DriverShortlistResult(
        List<DriverRouteFeatures> shortlistedFeatures,
        List<String> degradeReasons,
        List<MlStageMetadata> mlStageMetadata) {
}
