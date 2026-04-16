package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.cluster.DispatchPairClusterService;
import com.routechain.v2.cluster.EtaLegCacheFactory;
import com.routechain.v2.cluster.MicroClusterer;
import com.routechain.v2.cluster.OrderBuffer;
import com.routechain.v2.cluster.PairFeatureBuilder;
import com.routechain.v2.cluster.PairHardGateEvaluator;
import com.routechain.v2.cluster.PairSimilarityGraphBuilder;
import com.routechain.v2.cluster.PairSimilarityScorer;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;

import java.time.Instant;
import java.util.List;

final class TestDispatchV2Factory {
    private TestDispatchV2Factory() {
    }

    static DispatchV2CompatibleCore compatibleCore(RouteChainDispatchV2Properties properties) {
        DispatchV2Configuration configuration = new DispatchV2Configuration();
        DispatchV2Core core = core(properties);
        return configuration.dispatchV2CompatibleCore(properties, core);
    }

    static DispatchV2Core core(RouteChainDispatchV2Properties properties) {
        DispatchV2Configuration configuration = new DispatchV2Configuration();
        BaselineTravelTimeEstimator baselineTravelTimeEstimator = configuration.baselineTravelTimeEstimator();
        TrafficProfileService trafficProfileService = configuration.trafficProfileService(properties);
        WeatherContextService weatherContextService = configuration.weatherContextService(properties, new NoOpOpenMeteoClient());
        EtaFeatureBuilder etaFeatureBuilder = configuration.etaFeatureBuilder();
        EtaUncertaintyEstimator etaUncertaintyEstimator = configuration.etaUncertaintyEstimator();
        EtaService etaService = configuration.etaService(
                properties,
                baselineTravelTimeEstimator,
                trafficProfileService,
                weatherContextService,
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                etaFeatureBuilder,
                etaUncertaintyEstimator);
        DispatchEtaContextService dispatchEtaContextService = configuration.dispatchEtaContextService(properties, etaService);
        OrderBuffer orderBuffer = configuration.orderBuffer(properties);
        PairFeatureBuilder pairFeatureBuilder = configuration.pairFeatureBuilder(baselineTravelTimeEstimator);
        PairHardGateEvaluator pairHardGateEvaluator = configuration.pairHardGateEvaluator(properties);
        PairSimilarityScorer pairSimilarityScorer = configuration.pairSimilarityScorer(
                properties,
                pairHardGateEvaluator,
                new NoOpTabularScoringClient());
        EtaLegCacheFactory etaLegCacheFactory = configuration.etaLegCacheFactory(properties, etaService);
        PairSimilarityGraphBuilder pairSimilarityGraphBuilder = configuration.pairSimilarityGraphBuilder(
                properties,
                etaService,
                pairFeatureBuilder,
                pairSimilarityScorer);
        MicroClusterer microClusterer = configuration.microClusterer(properties);
        DispatchPairClusterService dispatchPairClusterService = configuration.dispatchPairClusterService(
                properties,
                orderBuffer,
                pairSimilarityGraphBuilder,
                pairSimilarityScorer,
                pairHardGateEvaluator,
                pairFeatureBuilder,
                etaLegCacheFactory,
                microClusterer);
        return configuration.dispatchV2Core(dispatchEtaContextService, dispatchPairClusterService);
    }

    static DispatchV2Request requestWithOrdersAndDriver() {
        Instant decisionTime = Instant.parse("2026-04-16T12:00:00Z");
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-core",
                List.of(
                        order("order-1", 10.7750, 106.7000, 10.7800, 106.7100, decisionTime, false),
                        order("order-2", 10.7760, 106.7010, 10.7810, 106.7120, decisionTime.plusSeconds(120), false),
                        order("order-3", 10.8200, 106.7600, 10.8300, 106.7700, decisionTime.plusSeconds(300), true)),
                List.of(new Driver("driver-1", new GeoPoint(10.7700, 106.6950))),
                List.of(),
                WeatherProfile.CLEAR,
                decisionTime);
    }

    static Order order(String orderId,
                       double pickupLat,
                       double pickupLon,
                       double dropLat,
                       double dropLon,
                       Instant readyAt,
                       boolean urgent) {
        return new Order(
                orderId,
                new GeoPoint(pickupLat, pickupLon),
                new GeoPoint(dropLat, dropLon),
                readyAt.minusSeconds(300),
                readyAt,
                20,
                urgent);
    }
}
