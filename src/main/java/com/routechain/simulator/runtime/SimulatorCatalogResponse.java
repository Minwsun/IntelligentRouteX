package com.routechain.simulator.runtime;

import com.routechain.simulator.calendar.ScenarioSliceDefinition;

import java.util.List;

public record SimulatorCatalogResponse(
        String scenarioCatalogId,
        List<String> runModes,
        List<String> monthRegimes,
        List<String> dayTypes,
        List<String> timeBuckets,
        List<String> stressModifiers,
        List<String> weatherModes,
        List<String> trafficModes,
        List<String> toggles,
        List<ScenarioSliceDefinition> canonicalSlices) {
}
