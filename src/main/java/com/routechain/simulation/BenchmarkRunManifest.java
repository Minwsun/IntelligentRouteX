package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Top-level manifest for reproducible benchmark batches.
 */
public record BenchmarkRunManifest(
        String schemaVersion,
        String manifestId,
        String batchName,
        Instant createdAt,
        String gitRevision,
        List<Long> seeds,
        List<Integer> driverProfiles,
        BenchmarkEnvironmentProfile environmentProfile,
        int warmupRuns,
        int measurementRuns,
        int timeLimitSeconds,
        long computeBudgetMs,
        String protocol,
        String notes,
        List<BenchmarkCaseSpec> cases,
        List<PolicyCandidateRecord> policyCandidates,
        List<CounterfactualRunSpec> counterfactualSpecs
) {
    public BenchmarkRunManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION : schemaVersion;
        seeds = seeds == null ? List.of() : List.copyOf(seeds);
        driverProfiles = driverProfiles == null ? List.of() : List.copyOf(driverProfiles);
        environmentProfile = environmentProfile == null
                ? BenchmarkEnvironmentProfile.detect("local-production-small", 50, SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name())
                : environmentProfile;
        warmupRuns = Math.max(0, warmupRuns);
        measurementRuns = Math.max(1, measurementRuns);
        cases = cases == null ? List.of() : List.copyOf(cases);
        policyCandidates = policyCandidates == null ? List.of() : List.copyOf(policyCandidates);
        counterfactualSpecs = counterfactualSpecs == null ? List.of() : List.copyOf(counterfactualSpecs);
        protocol = protocol == null || protocol.isBlank()
                ? "multi-seed, reproducible, confidence-interval"
                : protocol;
        notes = notes == null ? "" : notes;
    }
}
