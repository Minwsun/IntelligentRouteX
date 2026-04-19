package com.routechain.v2.route;

import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.cluster.EtaLegCache;
import com.routechain.v2.cluster.EtaLegCacheFactory;
import com.routechain.v2.harvest.emitters.DispatchHarvestService;
import com.routechain.v2.harvest.writers.NoOpHarvestWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DispatchRouteCandidateService {
    private final PickupAnchorSelector pickupAnchorSelector;
    private final CandidateDriverShortlister candidateDriverShortlister;
    private final DriverReranker driverReranker;
    private final EtaLegCacheFactory etaLegCacheFactory;
    private final DispatchHarvestService dispatchHarvestService;

    public DispatchRouteCandidateService(PickupAnchorSelector pickupAnchorSelector,
                                         CandidateDriverShortlister candidateDriverShortlister,
                                         DriverReranker driverReranker,
                                         EtaLegCacheFactory etaLegCacheFactory) {
        this(
                pickupAnchorSelector,
                candidateDriverShortlister,
                driverReranker,
                etaLegCacheFactory,
                new DispatchHarvestService(com.routechain.config.RouteChainDispatchV2Properties.defaults().getHarvest(), new NoOpHarvestWriter()));
    }

    public DispatchRouteCandidateService(PickupAnchorSelector pickupAnchorSelector,
                                         CandidateDriverShortlister candidateDriverShortlister,
                                         DriverReranker driverReranker,
                                         EtaLegCacheFactory etaLegCacheFactory,
                                         DispatchHarvestService dispatchHarvestService) {
        this.pickupAnchorSelector = pickupAnchorSelector;
        this.candidateDriverShortlister = candidateDriverShortlister;
        this.driverReranker = driverReranker;
        this.etaLegCacheFactory = etaLegCacheFactory;
        this.dispatchHarvestService = dispatchHarvestService;
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
        long pickupAnchorStartedAt = System.nanoTime();
        AnchorSelectionResult anchorSelection = pickupAnchorSelector.selectDetailed(bundleStage.bundleCandidates(), context);
        List<PickupAnchor> pickupAnchors = anchorSelection.selectedAnchors();
        long pickupAnchorElapsedMs = elapsedMs(pickupAnchorStartedAt);
        EtaLegCache etaLegCache = etaLegCacheFactory.create(
                request.traceId(),
                request.decisionTime(),
                request.weatherProfile() == null ? WeatherProfile.CLEAR : request.weatherProfile());
        long shortlistStartedAt = System.nanoTime();
        List<DriverCandidate> driverCandidates = new ArrayList<>();
        List<MlStageMetadata> mlStageMetadata = new ArrayList<>();
        List<String> stageDegradeReasons = new ArrayList<>();
        int rawShortlistedCount = 0;
        for (PickupAnchor pickupAnchor : pickupAnchors) {
            DriverShortlistDetailedResult shortlistResult = candidateDriverShortlister.shortlistDetailed(
                    request.traceId(),
                    context.availableDrivers(),
                    pickupAnchor,
                    context,
                    etaContext,
                    etaLegCache);
            List<DriverRouteFeatures> shortlisted = shortlistResult.shortlistedFeatures();
            rawShortlistedCount += shortlisted.size();
            stageDegradeReasons.addAll(shortlistResult.degradeReasons());
            mlStageMetadata.addAll(shortlistResult.mlStageMetadata());
            List<DriverCandidate> rerankedCandidates = driverReranker.rerank(pickupAnchor, shortlisted);
            emitDriverCandidates(request, pickupAnchor, shortlistResult, rerankedCandidates);
            driverCandidates.addAll(rerankedCandidates);
        }
        emitAnchorCandidates(request, anchorSelection);
        long shortlistElapsedMs = elapsedMs(shortlistStartedAt);
        List<String> degradeReasons = java.util.stream.Stream.concat(
                        driverCandidates.stream().flatMap(candidate -> candidate.degradeReasons().stream()),
                        stageDegradeReasons.stream())
                .distinct()
                .toList();
        return new DispatchRouteCandidateStage(
                "dispatch-route-candidate-stage/v1",
                pickupAnchors,
                summarizeAnchors(bundleStage.bundleCandidates().size(), pickupAnchors, degradeReasons),
                List.copyOf(driverCandidates),
                summarizeDrivers(bundleStage.bundleCandidates().size(), pickupAnchors.size(), rawShortlistedCount, driverCandidates.size(), degradeReasons),
                List.of(
                        DispatchStageLatency.measured("pickup-anchor", pickupAnchorElapsedMs, false),
                        DispatchStageLatency.measured("driver-shortlist/rerank", shortlistElapsedMs, false)),
                List.copyOf(mlStageMetadata.stream().distinct().toList()),
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

    DriverShortlistSummary summarizeDrivers(int bundleCount,
                                            int anchorCount,
                                            int shortlistedDriverCount,
                                            int rerankedDriverCount,
                                            List<String> degradeReasons) {
        return new DriverShortlistSummary(
                "driver-shortlist-summary/v1",
                bundleCount,
                anchorCount,
                shortlistedDriverCount,
                rerankedDriverCount,
                degradeReasons);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void emitAnchorCandidates(DispatchV2Request request, AnchorSelectionResult anchorSelection) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (AnchorCandidateTrace trace : anchorSelection.candidateTraces()) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("bundleId", trace.anchor().bundleId());
            payload.put("anchorOrderId", trace.anchor().anchorOrderId());
            payload.put("anchorRank", trace.anchor().anchorRank());
            payload.put("anchorFeatures", Map.of("bundleOrderSetSignature", trace.anchor().bundleOrderSetSignature(), "reasons", trace.anchor().reasons()));
            payload.put("anchorScore", trace.anchor().score());
            payload.put("selected", trace.retained());
            payload.put("rejectReason", trace.rejectReason());
            payloads.add(payload);
        }
        dispatchHarvestService.writeRecords("anchor-candidate", "pickup-anchor", request, payloads);
    }

    private void emitDriverCandidates(DispatchV2Request request,
                                      PickupAnchor pickupAnchor,
                                      DriverShortlistDetailedResult shortlistResult,
                                      List<DriverCandidate> rerankedCandidates) {
        Map<String, DriverCandidate> rerankedByDriverId = rerankedCandidates.stream()
                .collect(java.util.stream.Collectors.toMap(DriverCandidate::driverId, candidate -> candidate, (left, right) -> left));
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (DriverShortlistCandidateTrace trace : shortlistResult.candidateTraces()) {
            DriverCandidate reranked = rerankedByDriverId.get(trace.features().driverId());
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("bundleId", pickupAnchor.bundleId());
            payload.put("anchorOrderId", pickupAnchor.anchorOrderId());
            payload.put("driverId", trace.features().driverId());
            payload.put("driverFeatures", trace.features());
            payload.put("deterministicDriverFit", trace.features().driverFitScore());
            payload.put("tabularDriverFit", null);
            payload.put("finalDriverFit", trace.features().driverFitScore());
            payload.put("finalRerank", reranked == null ? null : reranked.rerankScore());
            payload.put("shortlistRank", reranked == null ? null : reranked.rank());
            payload.put("retained", trace.retained());
            payload.put("rejectReason", trace.rejectReason());
            payloads.add(payload);
            for (MlStageMetadata metadata : shortlistResult.mlStageMetadata()) {
                dispatchHarvestService.writeTeacherTrace(
                        "tabular-teacher-trace",
                        "driver-shortlist/rerank",
                        request,
                        "DRIVER_FIT",
                        pickupAnchor.bundleId() + "|" + pickupAnchor.anchorOrderId() + "|" + trace.features().driverId(),
                        metadata,
                        trace.features().driverFitScore(),
                        null,
                        null,
                        metadata.fallbackUsed(),
                        trace.rejectReason(),
                        Map.of("driverId", trace.features().driverId()));
            }
        }
        dispatchHarvestService.writeRecords("driver-candidate", "driver-shortlist/rerank", request, payloads);
    }
}
