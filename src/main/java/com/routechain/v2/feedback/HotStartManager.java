package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.HotStartState;

import java.util.ArrayList;
import java.util.List;

public final class HotStartManager {
    private final RouteChainDispatchV2Properties properties;
    private DispatchRuntimeReuseState previousReuseState;

    public HotStartManager(RouteChainDispatchV2Properties properties, ReuseStateService reuseStateService) {
        this.properties = properties;
        if (properties.isHotStartEnabled() && properties.getWarmHotStart().isLoadLatestSnapshotOnBoot()) {
            ReuseStateLoadResult loadResult = reuseStateService.loadLatest();
            if (loadResult.loaded()) {
                previousReuseState = loadResult.reuseState();
            }
        }
    }

    public HotStartReusePlan plan(EtaContext etaContext) {
        if (!properties.isHotStartEnabled()) {
            return new HotStartReusePlan(
                    "hot-start-reuse-plan/v1",
                    previousReuseState == null ? null : previousReuseState.traceId(),
                    false,
                    false,
                    false,
                    false,
                    previousReuseState,
                    List.of("hot-start-disabled"));
        }
        if (previousReuseState == null) {
            return new HotStartReusePlan(
                    "hot-start-reuse-plan/v1",
                    null,
                    false,
                    false,
                    false,
                    false,
                    null,
                    List.of("hot-start-no-previous-state"));
        }
        List<String> degradeReasons = new ArrayList<>();
        if (!ReuseStateBuilder.etaContextSignature(etaContext).equals(previousReuseState.etaContextSignature())) {
            degradeReasons.add("hot-start-eta-signature-drift");
        }
        boolean reuseEligible = degradeReasons.isEmpty();
        return new HotStartReusePlan(
                "hot-start-reuse-plan/v1",
                previousReuseState.traceId(),
                reuseEligible,
                reuseEligible,
                reuseEligible,
                reuseEligible,
                previousReuseState,
                List.copyOf(degradeReasons));
    }

    public HotStartState update(DispatchRuntimeReuseState currentReuseState,
                                HotStartReusePlan reusePlan,
                                HotStartAppliedReuse appliedReuse) {
        HotStartState currentState = new HotStartState(
                "hot-start-state/v2",
                reusePlan == null ? previousReuseState == null ? null : previousReuseState.traceId() : reusePlan.previousTraceId(),
                previousReuseState == null ? List.of() : previousReuseState.clusterSignatures(),
                previousReuseState == null ? List.of() : previousReuseState.bundleSignatures(),
                previousReuseState == null || previousReuseState.routeProposals() == null ? List.of() : previousReuseState.routeProposals().stream()
                        .map(proposal -> proposal.proposalId()
                                + "|" + proposal.bundleId()
                                + "|" + proposal.driverId()
                                + "|" + String.join(",", proposal.stopOrder()))
                        .sorted()
                        .toList(),
                List.of(),
                reusePlan != null && reusePlan.reuseEligible(),
                appliedReuse != null && appliedReuse.pairClusterReused(),
                appliedReuse != null && appliedReuse.bundlePoolReused(),
                appliedReuse != null && appliedReuse.routeProposalPoolReused(),
                appliedReuse == null ? 0 : appliedReuse.reusedBundleCount(),
                appliedReuse == null ? 0 : appliedReuse.reusedRouteProposalCount(),
                appliedReuse == null ? 0L : appliedReuse.estimatedSavedMs(),
                appliedReuse == null ? List.of() : appliedReuse.reusedStageNames(),
                java.util.stream.Stream.of(
                                reusePlan == null ? java.util.stream.Stream.<String>empty() : reusePlan.degradeReasons().stream(),
                                appliedReuse == null ? java.util.stream.Stream.<String>empty() : appliedReuse.degradeReasons().stream())
                        .flatMap(stream -> stream)
                        .distinct()
                        .toList());
        if (currentReuseState != null) {
            previousReuseState = currentReuseState;
        }
        return currentState;
    }
}
