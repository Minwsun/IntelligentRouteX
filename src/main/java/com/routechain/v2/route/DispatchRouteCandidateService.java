package com.routechain.v2.route;

import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.cluster.EtaLegCache;
import com.routechain.v2.cluster.EtaLegCacheFactory;

import java.util.ArrayList;
import java.util.List;

public final class DispatchRouteCandidateService {
    private final PickupAnchorSelector pickupAnchorSelector;
    private final CandidateDriverShortlister candidateDriverShortlister;
    private final DriverReranker driverReranker;
    private final EtaLegCacheFactory etaLegCacheFactory;

    public DispatchRouteCandidateService(PickupAnchorSelector pickupAnchorSelector,
                                         CandidateDriverShortlister candidateDriverShortlister,
                                         DriverReranker driverReranker,
                                         EtaLegCacheFactory etaLegCacheFactory) {
        this.pickupAnchorSelector = pickupAnchorSelector;
        this.candidateDriverShortlister = candidateDriverShortlister;
        this.driverReranker = driverReranker;
        this.etaLegCacheFactory = etaLegCacheFactory;
    }

    public DispatchRouteCandidateStage evaluate(DispatchV2Request request,
                                                EtaContext etaContext,
                                                DispatchPairClusterStage pairClusterStage,
                                                DispatchBundleStage bundleStage) {
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request.availableDrivers(),
                pairClusterStage,
                bundleStage);
        List<PickupAnchor> pickupAnchors = pickupAnchorSelector.select(bundleStage.bundleCandidates(), context);
        EtaLegCache etaLegCache = etaLegCacheFactory.create(
                request.traceId(),
                request.decisionTime(),
                request.weatherProfile() == null ? WeatherProfile.CLEAR : request.weatherProfile());
        List<DriverCandidate> driverCandidates = new ArrayList<>();
        for (PickupAnchor pickupAnchor : pickupAnchors) {
            List<DriverRouteFeatures> shortlisted = candidateDriverShortlister.shortlist(
                    context.availableDrivers(),
                    pickupAnchor,
                    context,
                    etaContext,
                    etaLegCache);
            driverCandidates.addAll(driverReranker.rerank(pickupAnchor, shortlisted));
        }
        List<String> degradeReasons = driverCandidates.stream()
                .flatMap(candidate -> candidate.degradeReasons().stream())
                .distinct()
                .toList();
        return new DispatchRouteCandidateStage(
                "dispatch-route-candidate-stage/v1",
                pickupAnchors,
                summarizeAnchors(bundleStage.bundleCandidates().size(), pickupAnchors, degradeReasons),
                List.copyOf(driverCandidates),
                summarizeDrivers(bundleStage.bundleCandidates().size(), pickupAnchors.size(), driverCandidates, degradeReasons),
                degradeReasons);
    }

    private PickupAnchorSummary summarizeAnchors(int bundleCount, List<PickupAnchor> pickupAnchors, List<String> degradeReasons) {
        long anchoredBundleCount = pickupAnchors.stream().map(PickupAnchor::bundleId).distinct().count();
        double averageAnchorsPerBundle = bundleCount == 0 ? 0.0 : ((double) pickupAnchors.size() / bundleCount);
        return new PickupAnchorSummary(
                "pickup-anchor-summary/v1",
                bundleCount,
                (int) anchoredBundleCount,
                averageAnchorsPerBundle,
                degradeReasons);
    }

    private DriverShortlistSummary summarizeDrivers(int bundleCount,
                                                    int anchorCount,
                                                    List<DriverCandidate> driverCandidates,
                                                    List<String> degradeReasons) {
        return new DriverShortlistSummary(
                "driver-shortlist-summary/v1",
                bundleCount,
                anchorCount,
                driverCandidates.size(),
                driverCandidates.size(),
                degradeReasons);
    }
}
