package com.routechain.v2.executor;

import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.PickupAnchor;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.selector.SelectedProposal;
import com.routechain.v2.selector.SelectorCandidate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SelectedProposalResolver {

    public SelectedProposalResolveResult resolve(SelectedProposal selectedProposal,
                                                 Map<String, SelectorCandidate> selectorCandidateByProposalId,
                                                 Map<String, RouteProposal> routeProposalByProposalId,
                                                 DispatchRouteCandidateStage routeCandidateStage,
                                                 DispatchCandidateContext context) {
        SelectorCandidate selectorCandidate = selectorCandidateByProposalId.get(selectedProposal.proposalId());
        RouteProposal routeProposal = routeProposalByProposalId.get(selectedProposal.proposalId());
        BundleCandidate bundleCandidate = selectorCandidate == null ? null : context.bundle(selectorCandidate.bundleId());
        PickupAnchor pickupAnchor = selectorCandidate == null ? null : routeCandidateStage.pickupAnchors().stream()
                .filter(anchor -> anchor.bundleId().equals(selectorCandidate.bundleId())
                        && anchor.anchorOrderId().equals(selectorCandidate.anchorOrderId()))
                .findFirst()
                .orElse(null);
        DriverCandidate driverCandidate = selectorCandidate == null ? null : routeCandidateStage.driverCandidates().stream()
                .filter(candidate -> candidate.bundleId().equals(selectorCandidate.bundleId())
                        && candidate.anchorOrderId().equals(selectorCandidate.anchorOrderId())
                        && candidate.driverId().equals(selectorCandidate.driverId()))
                .findFirst()
                .orElse(null);

        if (selectorCandidate == null || routeProposal == null || bundleCandidate == null || pickupAnchor == null || driverCandidate == null) {
            return new SelectedProposalResolveResult(
                    Optional.empty(),
                    new DispatchExecutionTrace(
                            List.of(selectedProposal.proposalId()),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            "selected-proposal-missing-context"),
                    List.of("executor-missing-selected-proposal-context"));
        }

        return new SelectedProposalResolveResult(
                Optional.of(new ResolvedSelectedProposal(
                        selectedProposal,
                        selectorCandidate,
                        routeProposal,
                        bundleCandidate,
                        pickupAnchor,
                        driverCandidate)),
                new DispatchExecutionTrace(
                        List.of(),
                        List.of(),
                        List.of("resolved:" + selectedProposal.proposalId()),
                        List.of(),
                        List.of(),
                        "selected-proposal-resolved"),
                List.of());
    }
}
