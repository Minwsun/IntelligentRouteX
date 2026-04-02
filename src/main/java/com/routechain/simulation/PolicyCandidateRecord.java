package com.routechain.simulation;

import java.util.List;

/**
 * Candidate dispatch policy metadata for counterfactual/challenger evaluation.
 */
public record PolicyCandidateRecord(
        String policyId,
        SolverType solverType,
        String weightsProfile,
        String gateProfile,
        List<String> eligibleScenarios
) {
    public PolicyCandidateRecord {
        policyId = policyId == null || policyId.isBlank() ? "unknown-policy" : policyId;
        solverType = solverType == null ? SolverType.OMEGA_ASSIGNMENT : solverType;
        weightsProfile = weightsProfile == null || weightsProfile.isBlank() ? "default" : weightsProfile;
        gateProfile = gateProfile == null || gateProfile.isBlank() ? "execution-first-default" : gateProfile;
        eligibleScenarios = eligibleScenarios == null ? List.of("all") : List.copyOf(eligibleScenarios);
    }
}
