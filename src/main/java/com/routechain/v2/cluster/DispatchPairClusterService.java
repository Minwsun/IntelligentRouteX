package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.HotStartReuseSummary;
import com.routechain.v2.feedback.ReuseStateBuilder;

import java.util.ArrayList;
import java.util.List;

public final class DispatchPairClusterService {
    private final OrderBuffer orderBuffer;
    private final PairSimilarityGraphBuilder pairSimilarityGraphBuilder;
    private final EtaLegCacheFactory etaLegCacheFactory;
    private final MicroClusterer microClusterer;

    public DispatchPairClusterService(RouteChainDispatchV2Properties properties,
                                      OrderBuffer orderBuffer,
                                      PairSimilarityGraphBuilder pairSimilarityGraphBuilder,
                                      EtaLegCacheFactory etaLegCacheFactory,
                                      MicroClusterer microClusterer) {
        this.orderBuffer = orderBuffer;
        this.pairSimilarityGraphBuilder = pairSimilarityGraphBuilder;
        this.etaLegCacheFactory = etaLegCacheFactory;
        this.microClusterer = microClusterer;
    }

    public DispatchPairClusterStage evaluate(DispatchV2Request request, EtaContext etaContext) {
        return evaluate(request, etaContext, null);
    }

    public DispatchPairClusterStage evaluate(DispatchV2Request request,
                                             EtaContext etaContext,
                                             PairClusterReuseInput reuseInput) {
        BufferedOrderWindow bufferedOrderWindow = orderBuffer.buffer(request);
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
                return new DispatchPairClusterStage(
                        "dispatch-pair-cluster-stage/v1",
                        bufferedOrderWindow,
                        reuseInput.reuseState().pairGraphSummary(),
                        reuseInput.reuseState().pairSimilarityGraph(),
                        reuseInput.reuseState().microClusters(),
                        reuseInput.reuseState().microClusterSummary(),
                        HotStartReuseSummary.reused(reuseInput.reuseState().microClusters().size()),
                        reuseInput.reuseState().pairClusterMlStageMetadata(),
                        reuseInput.reuseState().pairClusterDegradeReasons());
            }
            DispatchPairClusterStage freshStage = evaluateFresh(request, etaContext, bufferedOrderWindow);
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
                    freshStage.mlStageMetadata(),
                    List.copyOf(degradeReasons.stream().distinct().toList()));
        }
        return evaluateFresh(request, etaContext, bufferedOrderWindow);
    }

    private DispatchPairClusterStage evaluateFresh(DispatchV2Request request,
                                                   EtaContext etaContext,
                                                   BufferedOrderWindow bufferedOrderWindow) {
        EtaLegCache etaLegCache = etaLegCacheFactory.create(request.traceId(), request.decisionTime(), request.weatherProfile());
        PairSimilarityGraphBuildResult graphBuildResult = pairSimilarityGraphBuilder.build(
                bufferedOrderWindow,
                etaContext,
                etaLegCache);
        PairSimilarityGraph graph = graphBuildResult.graph();
        List<String> degradeReasons = new ArrayList<>(graphBuildResult.degradeReasons());
        PairGraphSummary pairGraphSummary = summarizeGraph(graphBuildResult);
        List<MicroCluster> microClusters = microClusterer.cluster(bufferedOrderWindow, graph);
        MicroClusterSummary microClusterSummary = summarizeClusters(microClusters, degradeReasons);
        return new DispatchPairClusterStage(
                "dispatch-pair-cluster-stage/v1",
                bufferedOrderWindow,
                pairGraphSummary,
                graph,
                microClusters,
                microClusterSummary,
                HotStartReuseSummary.none(),
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
}
