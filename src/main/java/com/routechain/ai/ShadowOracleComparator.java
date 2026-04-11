package com.routechain.ai;

import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.OrToolsShadowPolicySearch;

/**
 * Lightweight live shadow comparator. Real OR-Tools integration remains offline/shadow,
 * but this critic exposes disagreement pressure to the hot path.
 */
public final class ShadowOracleComparator {
    private final OrToolsShadowPolicySearch oracleSearch = new OrToolsShadowPolicySearch();

    public ShadowOracleScore score(DispatchPlan plan, GraphRouteState state) {
        if (plan == null || state == null) {
            return new ShadowOracleScore(0.0, 0.0, 0.0, oracleSearch.mode() + "+jsprit-shadow");
        }
        double oracle = clamp01(
                plan.getOnTimeProbability() * 0.28
                        + state.batchSynergy() * 0.20
                        + state.futureOpportunity() * 0.16
                        + state.dropCoherence() * 0.12
                        + state.graphAffinity() * 0.12
                        - state.deadheadPenalty() * 0.12);
        double challenger = clamp01(
                plan.getExecutionScore() * 0.26
                        + plan.getContinuationScore() * 0.16
                        + plan.getCoverageScore() * 0.10
                        + Math.max(0.0, 1.0 - plan.getExpectedPostCompletionEmptyKm() / 3.0) * 0.18
                        - plan.getBorrowedDependencyScore() * 0.10
                        - plan.getLateRisk() * 0.20);
        double disagreement = Math.abs(oracle - challenger);
        double penalty = disagreement > 0.22 ? (disagreement - 0.22) * 0.55 : 0.0;
        return new ShadowOracleScore(
                oracle,
                challenger,
                clamp01(penalty),
                oracleSearch.mode() + "+jsprit-shadow");
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
