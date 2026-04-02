package com.routechain.simulation;

/**
 * Decision-complete benchmark case descriptor used by manifests and harnesses.
 */
public record BenchmarkCaseSpec(
        String caseId,
        String track,
        String scenarioName,
        String serviceTier,
        String environmentProfile,
        String routeLatencyMode,
        String datasetFamily,
        String datasetName,
        int ticks,
        int drivers,
        double demandMultiplier,
        double trafficIntensity,
        String weatherProfile,
        long seed,
        int replicateIndex
) {}
