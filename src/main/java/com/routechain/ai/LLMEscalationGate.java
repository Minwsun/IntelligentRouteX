package com.routechain.ai;

import com.routechain.simulation.DispatchPlan;

import java.util.List;

/**
 * Determines whether a given decision should be escalated to the LLM shadow
 * plane for critique or explanation.
 */
public interface LLMEscalationGate {
    boolean shouldEscalate(DriverDecisionContext context,
                           DispatchPlan selectedPlan,
                           List<DispatchPlan> candidatePlans);
}
