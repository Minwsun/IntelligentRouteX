package com.routechain.v2.selector;

import com.routechain.v2.DispatchV2PlanCandidate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RepairSelector {

    public List<DispatchV2PlanCandidate> repair(List<DispatchV2PlanCandidate> selected,
                                                List<DispatchV2PlanCandidate> remaining) {
        if (selected == null || selected.isEmpty()) {
            return List.of();
        }
        Set<String> seenDrivers = new LinkedHashSet<>();
        Set<String> seenOrders = new LinkedHashSet<>();
        List<DispatchV2PlanCandidate> repaired = new ArrayList<>();
        for (DispatchV2PlanCandidate candidate : selected) {
            if (conflicts(candidate, seenDrivers, seenOrders)) {
                continue;
            }
            accept(candidate, seenDrivers, seenOrders);
            repaired.add(candidate);
        }
        if (remaining != null) {
            for (DispatchV2PlanCandidate candidate : remaining) {
                if (conflicts(candidate, seenDrivers, seenOrders)) {
                    continue;
                }
                accept(candidate, seenDrivers, seenOrders);
                repaired.add(candidate);
            }
        }
        return List.copyOf(repaired);
    }

    private boolean conflicts(DispatchV2PlanCandidate candidate, Set<String> seenDrivers, Set<String> seenOrders) {
        if (candidate == null || candidate.plan() == null) {
            return true;
        }
        if (seenDrivers.contains(candidate.plan().getDriver().getId())) {
            return true;
        }
        return candidate.plan().getOrders().stream().map(com.routechain.domain.Order::getId).anyMatch(seenOrders::contains);
    }

    private void accept(DispatchV2PlanCandidate candidate, Set<String> seenDrivers, Set<String> seenOrders) {
        seenDrivers.add(candidate.plan().getDriver().getId());
        candidate.plan().getOrders().stream().map(com.routechain.domain.Order::getId).forEach(seenOrders::add);
    }
}
