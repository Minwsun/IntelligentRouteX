package com.routechain.simulation;

import java.util.List;

/**
 * Optimizer abstraction for hot-path plan matching.
 */
public interface DispatchOptimizer {
    String optimizerId();

    List<DispatchPlan> solve(List<DispatchPlan> plans, String runId, long tick);
}
