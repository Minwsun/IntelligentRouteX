package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteProposalPruner {
    private final RouteChainDispatchV2Properties properties;

    public RouteProposalPruner(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public List<RouteProposalCandidate> prune(List<RouteProposalCandidate> candidates) {
        return pruneDetailed(candidates).retainedCandidates();
    }

    RouteProposalPruneResult pruneDetailed(List<RouteProposalCandidate> candidates) {
        java.util.List<RouteProposalPruneTrace> traces = new java.util.ArrayList<>();
        java.util.List<RouteProposalCandidate> retained = candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(RouteProposalCandidate::tupleKey, LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .values().stream()
                .flatMap(tupleCandidates -> pruneTuple(tupleCandidates, traces).stream())
                .sorted(comparator())
                .toList();
        java.util.Set<String> retainedIds = retained.stream().map(candidate -> candidate.proposal().proposalId()).collect(java.util.stream.Collectors.toSet());
        for (RouteProposalCandidate candidate : candidates) {
            if (traces.stream().noneMatch(trace -> trace.candidate().proposal().proposalId().equals(candidate.proposal().proposalId()))) {
                traces.add(new RouteProposalPruneTrace(
                        candidate,
                        retainedIds.contains(candidate.proposal().proposalId()),
                        retainedIds.contains(candidate.proposal().proposalId()) ? "" : "route-proposal-pruned"));
            }
        }
        return new RouteProposalPruneResult(retained, java.util.List.copyOf(traces));
    }

    Comparator<RouteProposalCandidate> comparator() {
        return Comparator.comparingDouble((RouteProposalCandidate candidate) -> candidate.proposal().routeValue()).reversed()
                .thenComparingDouble(candidate -> candidate.proposal().projectedPickupEtaMinutes())
                .thenComparing(candidate -> candidate.proposal().proposalId());
    }

    private List<RouteProposalCandidate> pruneTuple(List<RouteProposalCandidate> tupleCandidates,
                                                    java.util.List<RouteProposalPruneTrace> traces) {
        Map<String, RouteProposalCandidate> deduped = new LinkedHashMap<>();
        for (RouteProposalCandidate candidate : tupleCandidates.stream()
                .filter(candidate -> candidate.proposal().feasible())
                .sorted(comparator())
                .toList()) {
            String dedupeKey = candidate.proposal().source().name() + "|" + RouteProposalEngine.stopOrderSignature(candidate.proposal().stopOrder());
            if (deduped.containsKey(dedupeKey)) {
                traces.add(new RouteProposalPruneTrace(candidate, false, "route-proposal-deduped"));
                continue;
            }
            deduped.putIfAbsent(dedupeKey, candidate);
        }
        List<RouteProposalCandidate> ranked = deduped.values().stream()
                .sorted(comparator())
                .limit(Math.max(1, properties.getCandidate().getMaxRouteAlternatives()))
                .toList();
        java.util.Set<String> retainedIds = ranked.stream().map(candidate -> candidate.proposal().proposalId()).collect(java.util.stream.Collectors.toSet());
        for (RouteProposalCandidate candidate : deduped.values()) {
            traces.add(new RouteProposalPruneTrace(
                    candidate,
                    retainedIds.contains(candidate.proposal().proposalId()),
                    retainedIds.contains(candidate.proposal().proposalId()) ? "" : "route-proposal-top-n-truncated"));
        }
        return ranked;
    }
}
