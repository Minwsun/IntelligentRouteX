package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairSimilarityGraphBuilderTest {

    @Test
    void retainsOnlyAboveThresholdCompatibleEdges() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        BaselineTravelTimeEstimator baselineTravelTimeEstimator = new BaselineTravelTimeEstimator();
        EtaService etaService = new EtaService(
                properties,
                baselineTravelTimeEstimator,
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        PairSimilarityScorer scorer = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                new NoOpTabularScoringClient());
        PairSimilarityGraphBuilder builder = new PairSimilarityGraphBuilder(
                properties,
                new PairFeatureBuilder(baselineTravelTimeEstimator),
                scorer);
        Order closeA = ClusterTestFixtures.order("order-1", 10.7750, 106.7000, 10.7820, 106.7100, "2026-04-16T12:00:00Z", false);
        Order closeB = ClusterTestFixtures.order("order-2", 10.7758, 106.7008, 10.7825, 106.7108, "2026-04-16T12:04:00Z", false);
        Order far = ClusterTestFixtures.order("order-3", 10.8400, 106.7900, 10.8500, 106.8000, "2026-04-16T12:06:00Z", false);
        BufferedOrderWindow window = ClusterTestFixtures.window(List.of(closeA, closeB, far));

        EtaLegCache etaLegCache = new EtaLegCache(
                etaService,
                "trace-cluster",
                window.decisionTime(),
                com.routechain.domain.WeatherProfile.CLEAR,
                properties.getPair().getMlTimeout().toMillis());
        PairSimilarityGraphBuildResult buildResult = builder.build(
                window,
                ClusterTestFixtures.clearEtaContext(),
                etaLegCache);
        PairSimilarityGraph graph = buildResult.graph();

        assertEquals(3, graph.orderCount());
        assertEquals(1, graph.edgeCount());
        assertEquals("order-1", graph.edges().getFirst().leftOrderId());
        assertEquals("order-2", graph.edges().getFirst().rightOrderId());
        assertTrue(graph.edges().getFirst().weight() >= properties.getPair().getScoreThreshold());
        assertEquals(3, buildResult.candidatePairCount());
        assertEquals(1, buildResult.gatedPairCount());
    }
}
