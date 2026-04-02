package com.routechain.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Timefold-first optimizer wrapper for micro-batch online dispatch.
 *
 * Current implementation keeps deterministic behavior by using the existing
 * AssignmentSolver per micro-batch while preserving the same conflict rules.
 */
public final class TimefoldOnlineOptimizer implements DispatchOptimizer {
    private static final int MICRO_BATCH_SIZE = 180;
    private static final int MAX_BATCHES_PER_TICK = 8;
    private final AssignmentSolver fallbackSolver = new AssignmentSolver();
    private final boolean timefoldAvailable;

    public TimefoldOnlineOptimizer() {
        this.timefoldAvailable = isClassAvailable("ai.timefold.solver.core.api.solver.SolverFactory");
    }

    @Override
    public String optimizerId() {
        return timefoldAvailable
                ? "timefold-online-microbatch-v1"
                : "timefold-unavailable-fallback-assignment-v1";
    }

    @Override
    public List<DispatchPlan> solve(List<DispatchPlan> plans, String runId, long tick) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        List<DispatchPlan> sorted = plans.stream()
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .toList();
        List<List<DispatchPlan>> batches = partition(sorted, MICRO_BATCH_SIZE);
        List<DispatchPlan> selected = new ArrayList<>();
        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        int processedBatches = 0;

        for (List<DispatchPlan> batch : batches) {
            if (processedBatches++ >= MAX_BATCHES_PER_TICK) {
                break;
            }
            List<DispatchPlan> prefiltered = batch.stream()
                    .filter(plan -> !usedDrivers.contains(plan.getDriver().getId()))
                    .filter(plan -> plan.getOrders().stream().noneMatch(order -> usedOrders.contains(order.getId())))
                    .toList();
            if (prefiltered.isEmpty()) {
                continue;
            }
            List<DispatchPlan> batchSelected = fallbackSolver.solve(prefiltered);
            for (DispatchPlan plan : batchSelected) {
                if (usedDrivers.contains(plan.getDriver().getId())) {
                    continue;
                }
                boolean conflict = plan.getOrders().stream()
                        .anyMatch(order -> usedOrders.contains(order.getId()));
                if (conflict) {
                    continue;
                }
                selected.add(plan);
                usedDrivers.add(plan.getDriver().getId());
                plan.getOrders().forEach(order -> usedOrders.add(order.getId()));
            }
        }
        return selected;
    }

    private static List<List<DispatchPlan>> partition(List<DispatchPlan> plans, int size) {
        if (plans.size() <= size) {
            return List.of(plans);
        }
        List<List<DispatchPlan>> out = new ArrayList<>();
        for (int i = 0; i < plans.size(); i += size) {
            int end = Math.min(i + size, plans.size());
            out.add(plans.subList(i, end));
        }
        return out;
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
