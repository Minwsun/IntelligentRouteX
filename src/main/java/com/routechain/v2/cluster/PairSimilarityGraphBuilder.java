package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.EtaContext;
import com.routechain.v2.context.EtaService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PairSimilarityGraphBuilder {
    private final RouteChainDispatchV2Properties properties;
    private final EtaService etaService;
    private final PairFeatureBuilder pairFeatureBuilder;
    private final PairSimilarityScorer pairSimilarityScorer;

    public PairSimilarityGraphBuilder(RouteChainDispatchV2Properties properties,
                                      EtaService etaService,
                                      PairFeatureBuilder pairFeatureBuilder,
                                      PairSimilarityScorer pairSimilarityScorer) {
        this.properties = properties;
        this.etaService = etaService;
        this.pairFeatureBuilder = pairFeatureBuilder;
        this.pairSimilarityScorer = pairSimilarityScorer;
    }

    public PairSimilarityGraph build(BufferedOrderWindow window, EtaContext etaContext) {
        EtaLegCache etaLegCache = new EtaLegCache(
                etaService,
                new com.routechain.v2.DispatchV2Request(
                        "dispatch-v2-request/v1",
                        window.traceId(),
                        window.orders(),
                        List.of(),
                        List.of(),
                        etaContext.weatherBadSignal() ? com.routechain.domain.WeatherProfile.HEAVY_RAIN : com.routechain.domain.WeatherProfile.CLEAR,
                        window.decisionTime()),
                properties.getPair().getMlTimeout().toMillis());

        List<PairEdge> edges = new ArrayList<>();
        List<Order> orders = window.orders();
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                Order left = orders.get(i);
                Order right = orders.get(j);
                PairFeatureVector featureVector = pairFeatureBuilder.build(window, left, right, etaContext, etaLegCache);
                PairCompatibility compatibility = pairSimilarityScorer.score(featureVector);
                if (compatibility.hardGatePassed() && compatibility.score() >= properties.getPair().getScoreThreshold()) {
                    edges.add(new PairEdge(left.orderId(), right.orderId(), compatibility.score()));
                }
            }
        }
        List<PairEdge> sortedEdges = edges.stream()
                .sorted(Comparator.comparing(PairEdge::leftOrderId).thenComparing(PairEdge::rightOrderId))
                .toList();
        return new PairSimilarityGraph(
                "pair-similarity-graph/v1",
                orders.size(),
                sortedEdges.size(),
                sortedEdges);
    }
}

