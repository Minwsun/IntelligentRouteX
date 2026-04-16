package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
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
import com.routechain.v2.integration.OpenMeteoClient;
import com.routechain.v2.integration.TabularScoringClient;
import com.routechain.v2.integration.TomTomTrafficRefineClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchV2Configuration {

    @Bean
    BaselineTravelTimeEstimator baselineTravelTimeEstimator() {
        return new BaselineTravelTimeEstimator();
    }

    @Bean
    TrafficProfileService trafficProfileService(RouteChainDispatchV2Properties properties) {
        return new TrafficProfileService(properties);
    }

    @Bean
    OpenMeteoClient openMeteoClient() {
        return new NoOpOpenMeteoClient();
    }

    @Bean
    WeatherContextService weatherContextService(RouteChainDispatchV2Properties properties, OpenMeteoClient openMeteoClient) {
        return new WeatherContextService(properties, openMeteoClient);
    }

    @Bean
    TomTomTrafficRefineClient tomTomTrafficRefineClient() {
        return new NoOpTomTomTrafficRefineClient();
    }

    @Bean
    TabularScoringClient tabularScoringClient() {
        return new NoOpTabularScoringClient();
    }

    @Bean
    EtaFeatureBuilder etaFeatureBuilder() {
        return new EtaFeatureBuilder();
    }

    @Bean
    EtaUncertaintyEstimator etaUncertaintyEstimator() {
        return new EtaUncertaintyEstimator();
    }

    @Bean
    EtaService etaService(RouteChainDispatchV2Properties properties,
                          BaselineTravelTimeEstimator baselineTravelTimeEstimator,
                          TrafficProfileService trafficProfileService,
                          WeatherContextService weatherContextService,
                          TomTomTrafficRefineClient tomTomTrafficRefineClient,
                          TabularScoringClient tabularScoringClient,
                          EtaFeatureBuilder etaFeatureBuilder,
                          EtaUncertaintyEstimator etaUncertaintyEstimator) {
        return new EtaService(
                properties,
                baselineTravelTimeEstimator,
                trafficProfileService,
                weatherContextService,
                tomTomTrafficRefineClient,
                tabularScoringClient,
                etaFeatureBuilder,
                etaUncertaintyEstimator);
    }

    @Bean
    DispatchEtaContextService dispatchEtaContextService(RouteChainDispatchV2Properties properties, EtaService etaService) {
        return new DispatchEtaContextService(properties, etaService);
    }

    @Bean
    OrderBuffer orderBuffer(RouteChainDispatchV2Properties properties) {
        return new OrderBuffer(properties);
    }

    @Bean
    PairFeatureBuilder pairFeatureBuilder(BaselineTravelTimeEstimator baselineTravelTimeEstimator) {
        return new PairFeatureBuilder(baselineTravelTimeEstimator);
    }

    @Bean
    PairHardGateEvaluator pairHardGateEvaluator(RouteChainDispatchV2Properties properties) {
        return new PairHardGateEvaluator(properties);
    }

    @Bean
    PairSimilarityScorer pairSimilarityScorer(RouteChainDispatchV2Properties properties,
                                              PairHardGateEvaluator pairHardGateEvaluator,
                                              TabularScoringClient tabularScoringClient) {
        return new PairSimilarityScorer(properties, pairHardGateEvaluator, tabularScoringClient);
    }

    @Bean
    EtaLegCacheFactory etaLegCacheFactory(RouteChainDispatchV2Properties properties, EtaService etaService) {
        return new EtaLegCacheFactory(properties, etaService);
    }

    @Bean
    PairSimilarityGraphBuilder pairSimilarityGraphBuilder(RouteChainDispatchV2Properties properties,
                                                          EtaService etaService,
                                                          PairFeatureBuilder pairFeatureBuilder,
                                                          PairSimilarityScorer pairSimilarityScorer) {
        return new PairSimilarityGraphBuilder(properties, etaService, pairFeatureBuilder, pairSimilarityScorer);
    }

    @Bean
    MicroClusterer microClusterer(RouteChainDispatchV2Properties properties) {
        return new MicroClusterer(properties);
    }

    @Bean
    DispatchPairClusterService dispatchPairClusterService(RouteChainDispatchV2Properties properties,
                                                          OrderBuffer orderBuffer,
                                                          PairSimilarityGraphBuilder pairSimilarityGraphBuilder,
                                                          PairSimilarityScorer pairSimilarityScorer,
                                                          PairHardGateEvaluator pairHardGateEvaluator,
                                                          PairFeatureBuilder pairFeatureBuilder,
                                                          EtaLegCacheFactory etaLegCacheFactory,
                                                          MicroClusterer microClusterer) {
        return new DispatchPairClusterService(
                properties,
                orderBuffer,
                pairSimilarityGraphBuilder,
                pairSimilarityScorer,
                pairHardGateEvaluator,
                pairFeatureBuilder,
                etaLegCacheFactory,
                microClusterer);
    }

    @Bean
    DispatchV2Core dispatchV2Core(DispatchEtaContextService dispatchEtaContextService,
                                  DispatchPairClusterService dispatchPairClusterService) {
        return new DispatchV2Core(dispatchEtaContextService, dispatchPairClusterService);
    }

    @Bean
    DispatchV2CompatibleCore dispatchV2CompatibleCore(RouteChainDispatchV2Properties properties, DispatchV2Core dispatchV2Core) {
        return new DispatchV2CompatibleCore(properties, dispatchV2Core);
    }
}
