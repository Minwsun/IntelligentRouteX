package com.routechain.v2.scenario;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.context.FreshnessMetadata;
import com.routechain.v2.integration.ForecastResult;

import java.util.List;

record ForecastScenarioContext(
        ForecastResult demandShift,
        ForecastResult zoneBurst,
        ForecastResult postDropShift,
        FreshnessMetadata freshnessMetadata,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) {

    static ForecastScenarioContext empty() {
        ForecastResult notApplied = ForecastResult.notApplied("forecast-client-disabled");
        return new ForecastScenarioContext(
                notApplied,
                notApplied,
                notApplied,
                FreshnessMetadata.empty(),
                List.of(),
                List.of());
    }
}
