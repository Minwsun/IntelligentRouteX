package com.routechain.simulation;

import java.util.List;

/**
 * Reproducible run spec for policy-vs-policy counterfactual execution.
 */
public record CounterfactualRunSpec(
        String scenario,
        long seed,
        int driverProfile,
        List<String> policySet,
        long timeBudgetMs
) {
    public CounterfactualRunSpec {
        scenario = scenario == null || scenario.isBlank() ? "unknown-scenario" : scenario;
        driverProfile = Math.max(1, driverProfile);
        policySet = policySet == null || policySet.isEmpty() ? List.of("Legacy", "Omega-current") : List.copyOf(policySet);
        timeBudgetMs = Math.max(1_000L, timeBudgetMs);
    }
}
