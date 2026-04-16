package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;

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
        BufferedOrderWindow bufferedOrderWindow = orderBuffer.buffer(request);
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
