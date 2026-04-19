package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.EtaContext;
import com.routechain.v2.HotStartReuseSummary;
import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.feedback.ReuseStateBuilder;
import com.routechain.v2.harvest.emitters.DispatchHarvestService;
import com.routechain.v2.harvest.writers.NoOpHarvestWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DispatchPairClusterService {
    private final OrderBuffer orderBuffer;
    private final PairSimilarityGraphBuilder pairSimilarityGraphBuilder;
    private final EtaLegCacheFactory etaLegCacheFactory;
    private final MicroClusterer microClusterer;
    private final DispatchHarvestService dispatchHarvestService;

    public DispatchPairClusterService(RouteChainDispatchV2Properties properties,
                                      OrderBuffer orderBuffer,
                                      PairSimilarityGraphBuilder pairSimilarityGraphBuilder,
                                      EtaLegCacheFactory etaLegCacheFactory,
                                      MicroClusterer microClusterer) {
        this(
                properties,
                orderBuffer,
                pairSimilarityGraphBuilder,
                etaLegCacheFactory,
                microClusterer,
                new DispatchHarvestService(RouteChainDispatchV2Properties.defaults().getHarvest(), new NoOpHarvestWriter()));
    }

    public DispatchPairClusterService(RouteChainDispatchV2Properties properties,
                                      OrderBuffer orderBuffer,
                                      PairSimilarityGraphBuilder pairSimilarityGraphBuilder,
                                      EtaLegCacheFactory etaLegCacheFactory,
                                      MicroClusterer microClusterer,
                                      DispatchHarvestService dispatchHarvestService) {
        this.orderBuffer = orderBuffer;
        this.pairSimilarityGraphBuilder = pairSimilarityGraphBuilder;
        this.etaLegCacheFactory = etaLegCacheFactory;
        this.microClusterer = microClusterer;
        this.dispatchHarvestService = dispatchHarvestService;
    }

    public DispatchPairClusterStage evaluate(DispatchV2Request request, EtaContext etaContext) {
        return evaluate(request, etaContext, null);
    }

    public DispatchPairClusterStage evaluate(DispatchV2Request request,
                                             EtaContext etaContext,
                                             PairClusterReuseInput reuseInput) {
        long orderBufferStartedAt = System.nanoTime();
        BufferedOrderWindow bufferedOrderWindow = orderBuffer.buffer(request);
        long orderBufferElapsedMs = elapsedMs(orderBufferStartedAt);
        if (reuseInput != null && reuseInput.reuseState() != null) {
            List<String> reuseDegradeReasons = new ArrayList<>();
            if (!ReuseStateBuilder.etaContextSignature(etaContext).equals(reuseInput.reuseState().etaContextSignature())) {
                reuseDegradeReasons.add("hot-start-eta-signature-drift");
            }
            if (!ReuseStateBuilder.bufferedOrderWindowSignature(bufferedOrderWindow)
                    .equals(reuseInput.reuseState().bufferedOrderWindowSignature())) {
                reuseDegradeReasons.add("hot-start-buffer-signature-drift");
            }
            if (reuseDegradeReasons.isEmpty()
                    && reuseInput.reuseState().pairSimilarityGraph() != null
                    && reuseInput.reuseState().pairGraphSummary() != null) {
                long pairGraphStartedAt = System.nanoTime();
                PairGraphSummary reusedPairGraphSummary = reuseInput.reuseState().pairGraphSummary();
                PairSimilarityGraph reusedPairSimilarityGraph = reuseInput.reuseState().pairSimilarityGraph();
                long pairGraphElapsedMs = elapsedMs(pairGraphStartedAt);
                long microClusterStartedAt = System.nanoTime();
                List<MicroCluster> reusedMicroClusters = reuseInput.reuseState().microClusters();
                MicroClusterSummary reusedMicroClusterSummary = reuseInput.reuseState().microClusterSummary();
                long microClusterElapsedMs = elapsedMs(microClusterStartedAt);
                return new DispatchPairClusterStage(
                        "dispatch-pair-cluster-stage/v1",
                        bufferedOrderWindow,
                        reusedPairGraphSummary,
                        reusedPairSimilarityGraph,
                        reusedMicroClusters,
                        reusedMicroClusterSummary,
                        HotStartReuseSummary.reused(reuseInput.reuseState().microClusters().size()),
                        List.of(
                                DispatchStageLatency.measured("order-buffer", orderBufferElapsedMs, false),
                                DispatchStageLatency.measured("pair-graph", pairGraphElapsedMs, true),
                                DispatchStageLatency.measured("micro-cluster", microClusterElapsedMs, true)),
                        reuseInput.reuseState().pairClusterMlStageMetadata(),
                        reuseInput.reuseState().pairClusterDegradeReasons());
            }
            DispatchPairClusterStage freshStage = evaluateFresh(request, etaContext, bufferedOrderWindow, orderBufferElapsedMs);
            List<String> degradeReasons = new ArrayList<>(freshStage.degradeReasons());
            degradeReasons.addAll(reuseDegradeReasons);
            return new DispatchPairClusterStage(
                    freshStage.schemaVersion(),
                    freshStage.bufferedOrderWindow(),
                    freshStage.pairGraphSummary(),
                freshStage.pairSimilarityGraph(),
                freshStage.microClusters(),
                freshStage.microClusterSummary(),
                HotStartReuseSummary.none().withDegradeReasons(reuseDegradeReasons),
                freshStage.stageLatencies(),
                freshStage.mlStageMetadata(),
                List.copyOf(degradeReasons.stream().distinct().toList()));
        }
        return evaluateFresh(request, etaContext, bufferedOrderWindow, orderBufferElapsedMs);
    }

    private DispatchPairClusterStage evaluateFresh(DispatchV2Request request,
                                                   EtaContext etaContext,
                                                   BufferedOrderWindow bufferedOrderWindow,
                                                   long orderBufferElapsedMs) {
        EtaLegCache etaLegCache = etaLegCacheFactory.create(request.traceId(), request.decisionTime(), request.weatherProfile());
        long pairGraphStartedAt = System.nanoTime();
        PairSimilarityGraphBuildResult graphBuildResult = pairSimilarityGraphBuilder.build(
                bufferedOrderWindow,
                etaContext,
                etaLegCache);
        emitPairCandidates(request, graphBuildResult);
        long pairGraphElapsedMs = elapsedMs(pairGraphStartedAt);
        PairSimilarityGraph graph = graphBuildResult.graph();
        List<String> degradeReasons = new ArrayList<>(graphBuildResult.degradeReasons());
        PairGraphSummary pairGraphSummary = summarizeGraph(graphBuildResult);
        long microClusterStartedAt = System.nanoTime();
        List<MicroCluster> microClusters = microClusterer.cluster(bufferedOrderWindow, graph);
        long microClusterElapsedMs = elapsedMs(microClusterStartedAt);
        MicroClusterSummary microClusterSummary = summarizeClusters(microClusters, degradeReasons);
        return new DispatchPairClusterStage(
                "dispatch-pair-cluster-stage/v1",
                bufferedOrderWindow,
                pairGraphSummary,
                graph,
                microClusters,
                microClusterSummary,
                HotStartReuseSummary.none(),
                List.of(
                        DispatchStageLatency.measured("order-buffer", orderBufferElapsedMs, false),
                        DispatchStageLatency.measured("pair-graph", pairGraphElapsedMs, false),
                        DispatchStageLatency.measured("micro-cluster", microClusterElapsedMs, false)),
                graphBuildResult.mlStageMetadata(),
                List.copyOf(degradeReasons));
    }

    private PairGraphSummary summarizeGraph(PairSimilarityGraphBuildResult graphBuildResult) {
        double averageEdgeWeight = graphBuildResult.graph().edges().stream().mapToDouble(PairEdge::weight).average().orElse(0.0);
        return new PairGraphSummary(
                "pair-graph-summary/v1",
                graphBuildResult.candidatePairCount(),
                graphBuildResult.gatedPairCount(),
                graphBuildResult.graph().edgeCount(),
                averageEdgeWeight,
                graphBuildResult.degradeReasons());
    }

    private MicroClusterSummary summarizeClusters(List<MicroCluster> microClusters, List<String> degradeReasons) {
        int largestClusterSize = microClusters.stream().mapToInt(cluster -> cluster.orderIds().size()).max().orElse(0);
        int singletonCount = (int) microClusters.stream().filter(cluster -> cluster.orderIds().size() == 1).count();
        return new MicroClusterSummary(
                "micro-cluster-summary/v1",
                microClusters.size(),
                largestClusterSize,
                singletonCount,
                List.copyOf(degradeReasons));
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void emitPairCandidates(DispatchV2Request request, PairSimilarityGraphBuildResult graphBuildResult) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (PairScoringTrace trace : graphBuildResult.pairScoringTraces()) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            String pairKey = trace.featureVector().leftOrderId() + "|" + trace.featureVector().rightOrderId();
            payload.put("pairKey", pairKey);
            payload.put("leftOrderId", trace.featureVector().leftOrderId());
            payload.put("rightOrderId", trace.featureVector().rightOrderId());
            payload.put("pairFeatures", trace.featureVector());
            payload.put("hardGatePassed", trace.gateDecision().passed());
            payload.put("hardGateReasons", trace.gateDecision().reasons());
            payload.put("deterministicPairScore", trace.deterministicScore());
            payload.put("tabularPairScore", trace.tabularScore());
            payload.put("finalPairScore", trace.compatibility().score());
            payload.put("kept", trace.compatibility().hardGatePassed() && trace.compatibility().score() > 0.0);
            payload.put("dropped", !trace.compatibility().hardGatePassed() || trace.compatibility().score() <= 0.0);
            payload.put("edgeCreated", trace.compatibility().hardGatePassed() && trace.compatibility().score() > 0.0);
            payloads.add(payload);

            List<MlStageMetadata> metadata = trace.compatibility().mlStageMetadata();
            if (!metadata.isEmpty()) {
                MlStageMetadata current = metadata.getFirst();
                dispatchHarvestService.writeTeacherTrace(
                        "tabular-teacher-trace",
                        "pair-graph",
                        request,
                        "PAIR",
                        pairKey,
                        current,
                        trace.tabularScore(),
                        trace.tabularScoreResult().uncertainty(),
                        null,
                        trace.tabularScoreResult().fallbackUsed(),
                        trace.tabularScoreResult().degradeReason(),
                        Map.of("leftOrderId", trace.featureVector().leftOrderId(), "rightOrderId", trace.featureVector().rightOrderId()));
            }
        }
        dispatchHarvestService.writeRecords("pair-candidate", "pair-graph", request, payloads);
    }
}
