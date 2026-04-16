package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.BoundaryCandidateSelector;
import com.routechain.v2.bundle.BoundaryExpansionEngine;
import com.routechain.v2.bundle.BundleDominancePruner;
import com.routechain.v2.bundle.BundleFamilyEnumerator;
import com.routechain.v2.bundle.BundleScorer;
import com.routechain.v2.bundle.BundleSeedGenerator;
import com.routechain.v2.bundle.BundleValidator;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.bundle.DispatchBundleStageService;
import com.routechain.v2.cluster.DispatchPairClusterService;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.cluster.EtaLegCacheFactory;
import com.routechain.v2.cluster.MicroClusterer;
import com.routechain.v2.cluster.OrderBuffer;
import com.routechain.v2.cluster.PairFeatureBuilder;
import com.routechain.v2.cluster.PairHardGateEvaluator;
import com.routechain.v2.cluster.PairSimilarityGraphBuilder;
import com.routechain.v2.cluster.PairSimilarityScorer;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;

import java.time.Instant;
import java.util.List;

final class RouteTestFixtures {
    private RouteTestFixtures() {
    }

    static RouteChainDispatchV2Properties properties() {
        return RouteChainDispatchV2Properties.defaults();
    }

    static DispatchV2Request request() {
        Instant decisionTime = Instant.parse("2026-04-16T12:00:00Z");
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-route",
                List.of(
                        order("order-1", 10.7750, 106.7000, 10.7800, 106.7100, decisionTime, false),
                        order("order-2", 10.7760, 106.7010, 10.7810, 106.7120, decisionTime.plusSeconds(120), false),
                        order("order-3", 10.7770, 106.7020, 10.7820, 106.7130, decisionTime.plusSeconds(240), true)),
                List.of(
                        new Driver("driver-1", new GeoPoint(10.7700, 106.6950)),
                        new Driver("driver-2", new GeoPoint(10.7720, 106.6970)),
                        new Driver("driver-3", new GeoPoint(10.7780, 106.7040))),
                List.of(),
                WeatherProfile.CLEAR,
                decisionTime);
    }

    static EtaContext etaContext() {
        return new EtaContext(
                "dispatch-eta-context/v1",
                "trace-route",
                1,
                6.0,
                6.0,
                0.3,
                false,
                false,
                "corridor-a",
                "baseline-profile-weather");
    }

    static DispatchPairClusterStage pairClusterStage(RouteChainDispatchV2Properties properties) {
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
        return new DispatchPairClusterService(
                properties,
                new OrderBuffer(properties),
                new PairSimilarityGraphBuilder(
                        properties,
                        new PairFeatureBuilder(estimator),
                        new PairSimilarityScorer(properties, new PairHardGateEvaluator(properties), new NoOpTabularScoringClient())),
                new EtaLegCacheFactory(properties, etaService),
                new MicroClusterer(properties))
                .evaluate(request(), etaContext());
    }

    static DispatchBundleStage bundleStage(RouteChainDispatchV2Properties properties, DispatchPairClusterStage pairClusterStage) {
        return new DispatchBundleStageService(
                properties,
                new BoundaryCandidateSelector(properties),
                new BoundaryExpansionEngine(properties),
                new BundleSeedGenerator(properties),
                new BundleFamilyEnumerator(properties),
                new BundleValidator(properties),
                new BundleScorer(properties),
                new BundleDominancePruner())
                .evaluate(etaContext(), pairClusterStage);
    }

    static DispatchCandidateContext candidateContext(RouteChainDispatchV2Properties properties) {
        DispatchPairClusterStage pairClusterStage = pairClusterStage(properties);
        DispatchBundleStage bundleStage = bundleStage(properties, pairClusterStage);
        return new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                request().availableDrivers(),
                pairClusterStage,
                bundleStage);
    }

    static DispatchRouteCandidateService routeService(RouteChainDispatchV2Properties properties) {
        EtaService etaService = new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        EtaLegCacheFactory etaLegCacheFactory = new EtaLegCacheFactory(properties, etaService);
        return new DispatchRouteCandidateService(
                new PickupAnchorSelector(properties),
                new CandidateDriverShortlister(properties, new DriverRouteFeatureBuilder()),
                new DriverReranker(),
                etaLegCacheFactory);
    }

    static DispatchRouteCandidateStage routeCandidateStage(RouteChainDispatchV2Properties properties) {
        DispatchPairClusterStage pairClusterStage = pairClusterStage(properties);
        DispatchBundleStage bundleStage = bundleStage(properties, pairClusterStage);
        return routeService(properties).evaluate(request(), etaContext(), pairClusterStage, bundleStage);
    }

    static DispatchRouteProposalService routeProposalService(RouteChainDispatchV2Properties properties) {
        EtaService etaService = new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        EtaLegCacheFactory etaLegCacheFactory = new EtaLegCacheFactory(properties, etaService);
        return new DispatchRouteProposalService(
                new RouteProposalEngine(),
                new RouteProposalValidator(),
                new RouteValueScorer(),
                new RouteProposalPruner(properties),
                etaLegCacheFactory);
    }

    private static Order order(String orderId,
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
