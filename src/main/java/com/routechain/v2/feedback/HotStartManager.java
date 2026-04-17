package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.HotStartState;

import java.util.List;

public final class HotStartManager {
    private final RouteChainDispatchV2Properties properties;
    private DispatchRuntimeSnapshot previousSnapshot;

    public HotStartManager(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public HotStartState update(DispatchRuntimeSnapshot currentSnapshot) {
        if (!properties.isHotStartEnabled()) {
            return new HotStartState(
                    "hot-start-state/v1",
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    List.of("hot-start-disabled"));
        }
        if (currentSnapshot == null) {
            return new HotStartState(
                    "hot-start-state/v1",
                    previousSnapshot == null ? null : previousSnapshot.traceId(),
                    previousSnapshot == null ? List.of() : previousSnapshot.clusterSignatures(),
                    previousSnapshot == null ? List.of() : previousSnapshot.bundleSignatures(),
                    previousSnapshot == null ? List.of() : previousSnapshot.routeProposalSignatures(),
                    previousSnapshot == null ? List.of() : previousSnapshot.selectedProposalIds(),
                    false,
                    List.of("hot-start-snapshot-unavailable"));
        }

        HotStartState currentState;
        if (previousSnapshot == null) {
            currentState = new HotStartState(
                    "hot-start-state/v1",
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    List.of("hot-start-no-previous-state"));
        } else {
            boolean reuseEligible = previousSnapshot.clusterSignatures().equals(currentSnapshot.clusterSignatures())
                    && previousSnapshot.bundleSignatures().equals(currentSnapshot.bundleSignatures())
                    && previousSnapshot.routeProposalSignatures().equals(currentSnapshot.routeProposalSignatures());
            currentState = new HotStartState(
                    "hot-start-state/v1",
                    previousSnapshot.traceId(),
                    previousSnapshot.clusterSignatures(),
                    previousSnapshot.bundleSignatures(),
                    previousSnapshot.routeProposalSignatures(),
                    previousSnapshot.selectedProposalIds(),
                    reuseEligible,
                    reuseEligible ? List.of() : List.of("hot-start-signature-drift"));
        }
        previousSnapshot = currentSnapshot;
        return currentState;
    }
}
