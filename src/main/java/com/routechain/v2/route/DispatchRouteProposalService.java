package com.routechain.v2.route;

import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.cluster.EtaLegCache;
import com.routechain.v2.cluster.EtaLegCacheFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DispatchRouteProposalService {
    private final RouteProposalEngine routeProposalEngine;
    private final RouteProposalValidator routeProposalValidator;
    private final RouteValueScorer routeValueScorer;
    private final RouteProposalPruner routeProposalPruner;
    private final EtaLegCacheFactory etaLegCacheFactory;

    public DispatchRouteProposalService(RouteProposalEngine routeProposalEngine,
                                        RouteProposalValidator routeProposalValidator,
                                        RouteValueScorer routeValueScorer,
                                        RouteProposalPruner routeProposalPruner,
                                        EtaLegCacheFactory etaLegCacheFactory) {
        this.routeProposalEngine = routeProposalEngine;
        this.routeProposalValidator = routeProposalValidator;
        this.routeValueScorer = routeValueScorer;
        this.routeProposalPruner = routeProposalPruner;
        this.etaLegCacheFactory = etaLegCacheFactory;
    }

    public DispatchRouteProposalStage evaluate(DispatchV2Request request,
                                               EtaContext etaContext,
                                               DispatchPairClusterStage pairClusterStage,
                                               DispatchBundleStage bundleStage,
                                               DispatchRouteCandidateStage routeCandidateStage) {
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request.availableDrivers(),
                pairClusterStage,
                bundleStage);
        EtaLegCache etaLegCache = etaLegCacheFactory.create(
                request.traceId(),
                request.decisionTime(),
                request.weatherProfile() == null ? WeatherProfile.CLEAR : request.weatherProfile());
        List<RouteProposalCandidate> generated = routeProposalEngine.generate(
                routeCandidateStage.driverCandidates(),
                routeCandidateStage.pickupAnchors(),
                context,
                etaLegCache);
        List<RouteProposalCandidate> validated = generated.stream()
                .map(candidate -> routeProposalValidator.validate(candidate, context))
                .toList();
        List<RouteProposalCandidate> scored = validated.stream()
                .map(candidate -> routeValueScorer.score(candidate, context))
                .toList();
        List<RouteProposalCandidate> retained = routeProposalPruner.prune(scored);
        List<RouteProposal> routeProposals = retained.stream().map(RouteProposalCandidate::proposal).toList();
        List<String> degradeReasons = scored.stream()
                .flatMap(candidate -> candidate.proposal().degradeReasons().stream())
                .distinct()
                .toList();
        return new DispatchRouteProposalStage(
                "dispatch-route-proposal-stage/v1",
                routeProposals,
                summarize(routeCandidateStage.driverCandidates().size(), generated, retained, degradeReasons),
                degradeReasons);
    }

    private RouteProposalSummary summarize(int driverCandidateCount,
                                          List<RouteProposalCandidate> generated,
                                          List<RouteProposalCandidate> retained,
                                          List<String> degradeReasons) {
        Map<RouteProposalSource, Integer> sourceCounts = new EnumMap<>(RouteProposalSource.class);
        generated.forEach(candidate -> sourceCounts.merge(candidate.proposal().source(), 1, Integer::sum));
        int proposalTupleCount = (int) generated.stream().map(RouteProposalCandidate::tupleKey).distinct().count();
        return new RouteProposalSummary(
                "route-proposal-summary/v1",
                driverCandidateCount,
                proposalTupleCount,
                generated.size(),
                retained.size(),
                sourceCounts,
                List.copyOf(degradeReasons));
    }
}
