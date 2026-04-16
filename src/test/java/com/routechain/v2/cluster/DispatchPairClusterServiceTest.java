package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
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

class DispatchPairClusterServiceTest {

    @Test
    void pairSummaryMatchesGraphMetadataWithoutRecomputePath() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        BaselineTravelTimeEstimator estimator = new BaselineTravelTimeEstimator();
        EtaService etaService = new EtaService(
                properties,
                estimator,
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        OrderBuffer orderBuffer = new OrderBuffer(properties);
        PairFeatureBuilder pairFeatureBuilder = new PairFeatureBuilder(estimator);
        PairSimilarityScorer pairSimilarityScorer = new PairSimilarityScorer(
                properties,
                new PairHardGateEvaluator(properties),
                new NoOpTabularScoringClient());
        DispatchPairClusterService service = new DispatchPairClusterService(
                properties,
                orderBuffer,
                new PairSimilarityGraphBuilder(properties, pairFeatureBuilder, pairSimilarityScorer),
                new EtaLegCacheFactory(properties, etaService),
                new MicroClusterer(properties));

        DispatchV2Request request = ClusterTestFixtures.request(List.of(
                ClusterTestFixtures.order("order-1", 10.7750, 106.7000, 10.7820, 106.7100, "2026-04-16T12:00:00Z", false),
                ClusterTestFixtures.order("order-2", 10.7758, 106.7008, 10.7825, 106.7108, "2026-04-16T12:04:00Z", false),
                ClusterTestFixtures.order("order-3", 10.8400, 106.7900, 10.8500, 106.8000, "2026-04-16T12:06:00Z", false)));

        DispatchPairClusterStage stage = service.evaluate(request, ClusterTestFixtures.clearEtaContext());

        assertEquals(stage.pairSimilarityGraph().edgeCount(), stage.pairGraphSummary().edgeCount());
        assertEquals(3, stage.pairGraphSummary().candidatePairCount());
        assertTrue(stage.pairGraphSummary().gatedPairCount() >= stage.pairGraphSummary().edgeCount());
    }
}
