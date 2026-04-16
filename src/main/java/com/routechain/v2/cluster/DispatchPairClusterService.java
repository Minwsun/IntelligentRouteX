package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;

import java.util.ArrayList;
import java.util.List;

public final class DispatchPairClusterService {
    private final RouteChainDispatchV2Properties properties;
    private final OrderBuffer orderBuffer;
    private final PairSimilarityGraphBuilder pairSimilarityGraphBuilder;
    private final PairSimilarityScorer pairSimilarityScorer;
    private final PairHardGateEvaluator pairHardGateEvaluator;
    private final PairFeatureBuilder pairFeatureBuilder;
    private final EtaLegCacheFactory etaLegCacheFactory;
    private final MicroClusterer microClusterer;

    public DispatchPairClusterService(RouteChainDispatchV2Properties properties,
                                      OrderBuffer orderBuffer,
                                      PairSimilarityGraphBuilder pairSimilarityGraphBuilder,
                                      PairSimilarityScorer pairSimilarityScorer,
                                      PairHardGateEvaluator pairHardGateEvaluator,
                                      PairFeatureBuilder pairFeatureBuilder,
                                      EtaLegCacheFactory etaLegCacheFactory,
                                      MicroClusterer microClusterer) {
        this.properties = properties;
        this.orderBuffer = orderBuffer;
        this.pairSimilarityGraphBuilder = pairSimilarityGraphBuilder;
        this.pairSimilarityScorer = pairSimilarityScorer;
        this.pairHardGateEvaluator = pairHardGateEvaluator;
        this.pairFeatureBuilder = pairFeatureBuilder;
        this.etaLegCacheFactory = etaLegCacheFactory;
        this.microClusterer = microClusterer;
    }

    public DispatchPairClusterStage evaluate(DispatchV2Request request, EtaContext etaContext) {
        BufferedOrderWindow bufferedOrderWindow = orderBuffer.buffer(request);
        PairSimilarityGraph graph = pairSimilarityGraphBuilder.build(bufferedOrderWindow, etaContext);
        List<String> degradeReasons = new ArrayList<>();
        PairGraphSummary pairGraphSummary = summarizeGraph(bufferedOrderWindow, request, etaContext, graph, degradeReasons);
        List<MicroCluster> microClusters = microClusterer.cluster(bufferedOrderWindow, graph);
        MicroClusterSummary microClusterSummary = summarizeClusters(microClusters, degradeReasons);
        return new DispatchPairClusterStage(
                "dispatch-pair-cluster-stage/v1",
                bufferedOrderWindow,
                pairGraphSummary,
                microClusters,
                microClusterSummary,
                List.copyOf(degradeReasons));
    }

    private PairGraphSummary summarizeGraph(BufferedOrderWindow window,
                                            DispatchV2Request request,
                                            EtaContext etaContext,
                                            PairSimilarityGraph graph,
                                            List<String> degradeReasons) {
        int candidatePairCount = Math.max(0, (window.orderCount() * (window.orderCount() - 1)) / 2);
        int gatedPairCount = 0;
        EtaLegCache etaLegCache = etaLegCacheFactory.create(request);
        for (int i = 0; i < window.orders().size(); i++) {
            for (int j = i + 1; j < window.orders().size(); j++) {
                PairFeatureVector featureVector = pairFeatureBuilder.build(window, window.orders().get(i), window.orders().get(j), etaContext, etaLegCache);
                PairGateDecision gateDecision = pairHardGateEvaluator.evaluate(featureVector);
                if (gateDecision.passed()) {
                    gatedPairCount++;
                    PairCompatibility compatibility = pairSimilarityScorer.score(featureVector);
                    if (compatibility.degradeReasons().contains("pair-ml-unavailable-or-disabled-path")) {
                        degradeReasons.add("pair-ml-unavailable-or-disabled-path");
                    }
                }
            }
        }
        double averageEdgeWeight = graph.edges().stream().mapToDouble(PairEdge::weight).average().orElse(0.0);
        return new PairGraphSummary(
                "pair-graph-summary/v1",
                candidatePairCount,
                gatedPairCount,
                graph.edgeCount(),
                averageEdgeWeight,
                List.copyOf(degradeReasons));
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

