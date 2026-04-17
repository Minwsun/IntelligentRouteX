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
import com.routechain.v2.bundle.BoundaryCandidateSelector;
import com.routechain.v2.bundle.BoundaryExpansionEngine;
import com.routechain.v2.bundle.BundleDominancePruner;
import com.routechain.v2.bundle.BundleFamilyEnumerator;
import com.routechain.v2.bundle.BundleScorer;
import com.routechain.v2.bundle.BundleSeedGenerator;
import com.routechain.v2.bundle.BundleValidator;
import com.routechain.v2.bundle.DispatchBundleStageService;
import com.routechain.v2.route.CandidateDriverShortlister;
import com.routechain.v2.route.DispatchRouteCandidateService;
import com.routechain.v2.route.DispatchRouteProposalService;
import com.routechain.v2.route.DriverReranker;
import com.routechain.v2.route.DriverRouteFeatureBuilder;
import com.routechain.v2.route.PickupAnchorSelector;
import com.routechain.v2.route.RouteProposalEngine;
import com.routechain.v2.route.RouteProposalPruner;
import com.routechain.v2.route.RouteProposalValidator;
import com.routechain.v2.route.RouteValueScorer;
import com.routechain.v2.scenario.DispatchScenarioService;
import com.routechain.v2.scenario.RobustUtilityAggregator;
import com.routechain.v2.scenario.ScenarioEvaluator;
import com.routechain.v2.scenario.ScenarioGateEvaluator;
import com.routechain.v2.selector.ConflictGraphBuilder;
import com.routechain.v2.selector.DispatchSelectorService;
import com.routechain.v2.selector.GlobalSelector;
import com.routechain.v2.selector.GreedyRepairSelector;
import com.routechain.v2.selector.OrToolsSetPackingSolver;
import com.routechain.v2.selector.SelectorCandidateBuilder;
import com.routechain.v2.selector.SelectorSolver;
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
                                                          PairFeatureBuilder pairFeatureBuilder,
                                                          PairSimilarityScorer pairSimilarityScorer) {
        return new PairSimilarityGraphBuilder(properties, pairFeatureBuilder, pairSimilarityScorer);
    }

    @Bean
    MicroClusterer microClusterer(RouteChainDispatchV2Properties properties) {
        return new MicroClusterer(properties);
    }

    @Bean
    DispatchPairClusterService dispatchPairClusterService(RouteChainDispatchV2Properties properties,
                                                          OrderBuffer orderBuffer,
                                                          PairSimilarityGraphBuilder pairSimilarityGraphBuilder,
                                                          EtaLegCacheFactory etaLegCacheFactory,
                                                          MicroClusterer microClusterer) {
        return new DispatchPairClusterService(
                properties,
                orderBuffer,
                pairSimilarityGraphBuilder,
                etaLegCacheFactory,
                microClusterer);
    }

    @Bean
    BoundaryCandidateSelector boundaryCandidateSelector(RouteChainDispatchV2Properties properties) {
        return new BoundaryCandidateSelector(properties);
    }

    @Bean
    BoundaryExpansionEngine boundaryExpansionEngine(RouteChainDispatchV2Properties properties) {
        return new BoundaryExpansionEngine(properties);
    }

    @Bean
    BundleSeedGenerator bundleSeedGenerator(RouteChainDispatchV2Properties properties) {
        return new BundleSeedGenerator(properties);
    }

    @Bean
    BundleFamilyEnumerator bundleFamilyEnumerator(RouteChainDispatchV2Properties properties) {
        return new BundleFamilyEnumerator(properties);
    }

    @Bean
    BundleValidator bundleValidator(RouteChainDispatchV2Properties properties) {
        return new BundleValidator(properties);
    }

    @Bean
    BundleScorer bundleScorer(RouteChainDispatchV2Properties properties) {
        return new BundleScorer(properties);
    }

    @Bean
    BundleDominancePruner bundleDominancePruner() {
        return new BundleDominancePruner();
    }

    @Bean
    DispatchBundleStageService dispatchBundleStageService(RouteChainDispatchV2Properties properties,
                                                          BoundaryCandidateSelector boundaryCandidateSelector,
                                                          BoundaryExpansionEngine boundaryExpansionEngine,
                                                          BundleSeedGenerator bundleSeedGenerator,
                                                          BundleFamilyEnumerator bundleFamilyEnumerator,
                                                          BundleValidator bundleValidator,
                                                          BundleScorer bundleScorer,
                                                          BundleDominancePruner bundleDominancePruner) {
        return new DispatchBundleStageService(
                properties,
                boundaryCandidateSelector,
                boundaryExpansionEngine,
                bundleSeedGenerator,
                bundleFamilyEnumerator,
                bundleValidator,
                bundleScorer,
                bundleDominancePruner);
    }

    @Bean
    PickupAnchorSelector pickupAnchorSelector(RouteChainDispatchV2Properties properties) {
        return new PickupAnchorSelector(properties);
    }

    @Bean
    DriverRouteFeatureBuilder driverRouteFeatureBuilder() {
        return new DriverRouteFeatureBuilder();
    }

    @Bean
    CandidateDriverShortlister candidateDriverShortlister(RouteChainDispatchV2Properties properties,
                                                          DriverRouteFeatureBuilder driverRouteFeatureBuilder) {
        return new CandidateDriverShortlister(properties, driverRouteFeatureBuilder);
    }

    @Bean
    DriverReranker driverReranker() {
        return new DriverReranker();
    }

    @Bean
    DispatchRouteCandidateService dispatchRouteCandidateService(PickupAnchorSelector pickupAnchorSelector,
                                                                CandidateDriverShortlister candidateDriverShortlister,
                                                                DriverReranker driverReranker,
                                                                EtaLegCacheFactory etaLegCacheFactory) {
        return new DispatchRouteCandidateService(
                pickupAnchorSelector,
                candidateDriverShortlister,
                driverReranker,
                etaLegCacheFactory);
    }

    @Bean
    RouteProposalEngine routeProposalEngine() {
        return new RouteProposalEngine();
    }

    @Bean
    RouteProposalValidator routeProposalValidator() {
        return new RouteProposalValidator();
    }

    @Bean
    RouteValueScorer routeValueScorer() {
        return new RouteValueScorer();
    }

    @Bean
    RouteProposalPruner routeProposalPruner(RouteChainDispatchV2Properties properties) {
        return new RouteProposalPruner(properties);
    }

    @Bean
    DispatchRouteProposalService dispatchRouteProposalService(RouteProposalEngine routeProposalEngine,
                                                              RouteProposalValidator routeProposalValidator,
                                                              RouteValueScorer routeValueScorer,
                                                              RouteProposalPruner routeProposalPruner,
                                                              EtaLegCacheFactory etaLegCacheFactory) {
        return new DispatchRouteProposalService(
                routeProposalEngine,
                routeProposalValidator,
                routeValueScorer,
                routeProposalPruner,
                etaLegCacheFactory);
    }

    @Bean
    ScenarioGateEvaluator scenarioGateEvaluator(RouteChainDispatchV2Properties properties) {
        return new ScenarioGateEvaluator(properties);
    }

    @Bean
    ScenarioEvaluator scenarioEvaluator(RouteChainDispatchV2Properties properties) {
        return new ScenarioEvaluator(properties);
    }

    @Bean
    RobustUtilityAggregator robustUtilityAggregator() {
        return new RobustUtilityAggregator();
    }

    @Bean
    DispatchScenarioService dispatchScenarioService(ScenarioGateEvaluator scenarioGateEvaluator,
                                                    ScenarioEvaluator scenarioEvaluator,
                                                    RobustUtilityAggregator robustUtilityAggregator) {
        return new DispatchScenarioService(
                scenarioGateEvaluator,
                scenarioEvaluator,
                robustUtilityAggregator);
    }

    @Bean
    SelectorCandidateBuilder selectorCandidateBuilder(RouteChainDispatchV2Properties properties) {
        return new SelectorCandidateBuilder(properties);
    }

    @Bean
    ConflictGraphBuilder conflictGraphBuilder() {
        return new ConflictGraphBuilder();
    }

    @Bean
    GreedyRepairSelector greedyRepairSelector() {
        return new GreedyRepairSelector();
    }

    @Bean
    SelectorSolver selectorSolver() {
        return new OrToolsSetPackingSolver();
    }

    @Bean
    GlobalSelector globalSelector(RouteChainDispatchV2Properties properties,
                                  GreedyRepairSelector greedyRepairSelector,
                                  SelectorSolver selectorSolver) {
        return new GlobalSelector(properties, greedyRepairSelector, selectorSolver);
    }

    @Bean
    DispatchSelectorService dispatchSelectorService(SelectorCandidateBuilder selectorCandidateBuilder,
                                                    ConflictGraphBuilder conflictGraphBuilder,
                                                    GlobalSelector globalSelector) {
        return new DispatchSelectorService(selectorCandidateBuilder, conflictGraphBuilder, globalSelector);
    }

    @Bean
    DispatchV2Core dispatchV2Core(DispatchEtaContextService dispatchEtaContextService,
                                  DispatchPairClusterService dispatchPairClusterService,
                                  DispatchBundleStageService dispatchBundleStageService,
                                  DispatchRouteCandidateService dispatchRouteCandidateService,
                                  DispatchRouteProposalService dispatchRouteProposalService,
                                  DispatchScenarioService dispatchScenarioService,
                                  DispatchSelectorService dispatchSelectorService) {
        return new DispatchV2Core(
                dispatchEtaContextService,
                dispatchPairClusterService,
                dispatchBundleStageService,
                dispatchRouteCandidateService,
                dispatchRouteProposalService,
                dispatchScenarioService,
                dispatchSelectorService);
    }

    @Bean
    DispatchV2CompatibleCore dispatchV2CompatibleCore(RouteChainDispatchV2Properties properties, DispatchV2Core dispatchV2Core) {
        return new DispatchV2CompatibleCore(properties, dispatchV2Core);
    }
}
