package com.routechain.ai;

import com.routechain.simulation.DispatchPlan;

import java.util.Comparator;
import java.util.List;

/**
 * Heuristic escalation gate for shadow/advisory LLM usage.
 */
public final class DefaultLLMEscalationGate implements LLMEscalationGate {
    @Override
    public boolean shouldEscalate(DriverDecisionContext context,
                                  DispatchPlan selectedPlan,
                                  List<DispatchPlan> candidatePlans) {
        if (selectedPlan == null || selectedPlan.getOrders().isEmpty()) {
            return false;
        }
        if (context.harshWeatherStress() || context.stressRegime() == StressRegime.SEVERE_STRESS) {
            return false;
        }
        if (selectedPlan.getConfidence() < 0.58 || selectedPlan.getBundleSize() >= 4) {
            return true;
        }
        if (selectedPlan.getDeliveryCorridorScore() < 0.58
                || selectedPlan.getLastDropLandingScore() < 0.50) {
            return true;
        }
        if (candidatePlans == null || candidatePlans.size() < 2) {
            return false;
        }
        List<DispatchPlan> ranked = candidatePlans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .limit(2)
                .toList();
        if (ranked.size() < 2) {
            return false;
        }
        double gap = ranked.get(0).getTotalScore() - ranked.get(1).getTotalScore();
        return gap <= 0.18;
    }
}
