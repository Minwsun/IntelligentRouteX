package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.EtaContext;
import com.routechain.v2.MlStageMetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PairSimilarityGraphBuilder {
    private final RouteChainDispatchV2Properties properties;
    private final PairFeatureBuilder pairFeatureBuilder;
    private final PairSimilarityScorer pairSimilarityScorer;

    public PairSimilarityGraphBuilder(RouteChainDispatchV2Properties properties,
                                      PairFeatureBuilder pairFeatureBuilder,
                                      PairSimilarityScorer pairSimilarityScorer) {
        this.properties = properties;
        this.pairFeatureBuilder = pairFeatureBuilder;
        this.pairSimilarityScorer = pairSimilarityScorer;
    }

    public PairSimilarityGraphBuildResult build(BufferedOrderWindow window,
                                                EtaContext etaContext,
                                                EtaLegCache etaLegCache) {
        List<PairEdge> edges = new ArrayList<>();
        List<String> degradeReasons = new ArrayList<>();
        List<MlStageMetadata> mlStageMetadata = new ArrayList<>();
        List<Order> orders = window.orders();
        int gatedPairCount = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                Order left = orders.get(i);
                Order right = orders.get(j);
                PairFeatureVector featureVector = pairFeatureBuilder.build(window, left, right, etaContext, etaLegCache);
                PairCompatibility compatibility = pairSimilarityScorer.score(featureVector);
                if (compatibility.hardGatePassed()) {
                    gatedPairCount++;
                }
                mlStageMetadata.addAll(compatibility.mlStageMetadata());
                if (compatibility.degradeReasons().contains("pair-ml-unavailable")) {
                    degradeReasons.add("pair-ml-unavailable");
                }
                if (compatibility.hardGatePassed() && compatibility.score() >= properties.getPair().getScoreThreshold()) {
                    edges.add(new PairEdge(left.orderId(), right.orderId(), compatibility.score()));
                }
            }
        }
        List<PairEdge> sortedEdges = edges.stream()
                .sorted(Comparator.comparing(PairEdge::leftOrderId).thenComparing(PairEdge::rightOrderId))
                .toList();
        return new PairSimilarityGraphBuildResult(
                new PairSimilarityGraph(
                        "pair-similarity-graph/v1",
                        orders.size(),
                        sortedEdges.size(),
                        sortedEdges),
                Math.max(0, (orders.size() * (orders.size() - 1)) / 2),
                gatedPairCount,
                List.copyOf(mlStageMetadata.stream().distinct().toList()),
                List.copyOf(degradeReasons.stream().distinct().toList()));
    }
}
