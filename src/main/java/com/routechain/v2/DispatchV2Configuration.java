package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.feedback.DecisionLogAssembler;
import com.routechain.v2.feedback.DecisionLogService;
import com.routechain.v2.feedback.DecisionLogWriter;
import com.routechain.v2.feedback.DispatchReplayComparator;
import com.routechain.v2.feedback.DispatchReplayLoader;
import com.routechain.v2.feedback.DispatchReplayRecorder;
import com.routechain.v2.feedback.DispatchReplayRunner;
import com.routechain.v2.feedback.FeedbackStorageMode;
import com.routechain.v2.feedback.FileDecisionLogWriter;
import com.routechain.v2.feedback.FileReplayStore;
import com.routechain.v2.feedback.FileSnapshotStore;
import com.routechain.v2.feedback.HotStartManager;
import com.routechain.v2.feedback.InMemoryDecisionLogWriter;
import com.routechain.v2.feedback.InMemoryReplayStore;
import com.routechain.v2.feedback.InMemorySnapshotStore;
import com.routechain.v2.feedback.PostDispatchHardeningService;
import com.routechain.v2.feedback.ReplayStore;
import com.routechain.v2.feedback.SnapshotBuilder;
import com.routechain.v2.feedback.SnapshotService;
import com.routechain.v2.feedback.SnapshotStore;
import com.routechain.v2.feedback.WarmStartManager;
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
import com.routechain.v2.executor.DispatchAssignmentBuilder;
import com.routechain.v2.executor.DispatchExecutor;
import com.routechain.v2.executor.DispatchExecutorService;
import com.routechain.v2.executor.ExecutionConflictValidator;
import com.routechain.v2.executor.SelectedProposalResolver;
import com.routechain.v2.selector.ConflictGraphBuilder;
import com.routechain.v2.selector.DispatchSelectorService;
import com.routechain.v2.selector.GlobalSelector;
import com.routechain.v2.selector.GreedyRepairSelector;
import com.routechain.v2.selector.OrToolsSetPackingSolver;
import com.routechain.v2.selector.SelectorCandidateBuilder;
import com.routechain.v2.selector.SelectorSolver;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.HttpOpenMeteoClient;
import com.routechain.v2.integration.HttpTomTomTrafficRefineClient;
import com.routechain.v2.integration.GreedRlClient;
import com.routechain.v2.integration.HttpGreedRlClient;
import com.routechain.v2.integration.HttpRouteFinderClient;
import com.routechain.v2.integration.HttpTabularScoringClient;
import com.routechain.v2.integration.OpenMeteoClient;
import com.routechain.v2.integration.RouteFinderClient;
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
    OpenMeteoClient openMeteoClient(RouteChainDispatchV2Properties properties) {
        if (!properties.isOpenMeteoEnabled() || !properties.getWeather().isEnabled()) {
            return new NoOpOpenMeteoClient();
        }
        return new HttpOpenMeteoClient(
                properties.getWeather().getBaseUrl(),
                properties.getWeather().getConnectTimeout(),
                properties.getWeather().getReadTimeout(),
                properties);
    }

    @Bean
    TomTomTrafficRefineClient tomTomTrafficRefineClient(RouteChainDispatchV2Properties properties) {
        if (!properties.isTomtomEnabled() || !properties.getTraffic().isEnabled()) {
            return new NoOpTomTomTrafficRefineClient();
        }
        return new HttpTomTomTrafficRefineClient(
                properties.getTraffic().getBaseUrl(),
                properties.getTraffic().getConnectTimeout(),
                properties.getTraffic().getReadTimeout(),
                new com.routechain.v2.context.TrafficRefineMapper());
    }

    @Bean
    TabularScoringClient tabularScoringClient(RouteChainDispatchV2Properties properties) {
        if (!properties.isMlEnabled() || !properties.getMl().getTabular().isEnabled()) {
            return new NoOpTabularScoringClient();
        }
        HttpTabularScoringClient client = new HttpTabularScoringClient(
                properties.getMl().getTabular().getBaseUrl(),
                properties.getMl().getTabular().getConnectTimeout(),
                properties.getMl().getTabular().getReadTimeout(),
                java.nio.file.Path.of("services", "models", "model-manifest.yaml"));
        if (properties.isSidecarRequired() && !client.readyState().ready()) {
            throw new IllegalStateException("Tabular worker is required but not ready: " + client.readyState().reason());
        }
        return client;
    }

    @Bean
    RouteFinderClient routeFinderClient(RouteChainDispatchV2Properties properties) {
        if (!properties.isMlEnabled() || !properties.getMl().getRoutefinder().isEnabled()) {
            return new NoOpRouteFinderClient();
        }
        HttpRouteFinderClient client = new HttpRouteFinderClient(
                properties.getMl().getRoutefinder().getBaseUrl(),
                properties.getMl().getRoutefinder().getConnectTimeout(),
                properties.getMl().getRoutefinder().getReadTimeout(),
                java.nio.file.Path.of("services", "models", "model-manifest.yaml"));
        if (properties.isSidecarRequired() && !client.readyState().ready()) {
            throw new IllegalStateException("RouteFinder worker is required but not ready: " + client.readyState().reason());
        }
        return client;
    }

    @Bean
    GreedRlClient greedRlClient(RouteChainDispatchV2Properties properties) {
        if (!properties.isMlEnabled() || !properties.getMl().getGreedrl().isEnabled()) {
            return new NoOpGreedRlClient();
        }
        HttpGreedRlClient client = new HttpGreedRlClient(
                properties.getMl().getGreedrl().getBaseUrl(),
                properties.getMl().getGreedrl().getConnectTimeout(),
                properties.getMl().getGreedrl().getReadTimeout(),
                java.nio.file.Path.of("services", "models", "model-manifest.yaml"));
        if (properties.isSidecarRequired() && !client.readyState().ready()) {
            throw new IllegalStateException("GreedRL worker is required but not ready: " + client.readyState().reason());
        }
        return client;
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
                                                          BundleDominancePruner bundleDominancePruner,
                                                          GreedRlClient greedRlClient) {
        return new DispatchBundleStageService(
                properties,
                boundaryCandidateSelector,
                boundaryExpansionEngine,
                bundleSeedGenerator,
                bundleFamilyEnumerator,
                bundleValidator,
                bundleScorer,
                bundleDominancePruner,
                greedRlClient);
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
                                                          DriverRouteFeatureBuilder driverRouteFeatureBuilder,
                                                          TabularScoringClient tabularScoringClient) {
        return new CandidateDriverShortlister(properties, driverRouteFeatureBuilder, tabularScoringClient);
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
    RouteValueScorer routeValueScorer(RouteChainDispatchV2Properties properties,
                                      TabularScoringClient tabularScoringClient) {
        return new RouteValueScorer(properties, tabularScoringClient);
    }

    @Bean
    RouteProposalPruner routeProposalPruner(RouteChainDispatchV2Properties properties) {
        return new RouteProposalPruner(properties);
    }

    @Bean
    DispatchRouteProposalService dispatchRouteProposalService(RouteChainDispatchV2Properties properties,
                                                              RouteProposalEngine routeProposalEngine,
                                                              RouteProposalValidator routeProposalValidator,
                                                              RouteValueScorer routeValueScorer,
                                                              RouteProposalPruner routeProposalPruner,
                                                              EtaLegCacheFactory etaLegCacheFactory,
                                                              RouteFinderClient routeFinderClient) {
        return new DispatchRouteProposalService(
                properties,
                routeProposalEngine,
                routeProposalValidator,
                routeValueScorer,
                routeProposalPruner,
                etaLegCacheFactory,
                routeFinderClient);
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
    SelectorSolver selectorSolver(RouteChainDispatchV2Properties properties) {
        return new OrToolsSetPackingSolver(properties);
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
    DispatchAssignmentBuilder dispatchAssignmentBuilder() {
        return new DispatchAssignmentBuilder();
    }

    @Bean
    SelectedProposalResolver selectedProposalResolver() {
        return new SelectedProposalResolver();
    }

    @Bean
    ExecutionConflictValidator executionConflictValidator() {
        return new ExecutionConflictValidator();
    }

    @Bean
    DispatchExecutor dispatchExecutor(SelectedProposalResolver selectedProposalResolver,
                                      ExecutionConflictValidator executionConflictValidator,
                                      DispatchAssignmentBuilder dispatchAssignmentBuilder) {
        return new DispatchExecutor(selectedProposalResolver, executionConflictValidator, dispatchAssignmentBuilder);
    }

    @Bean
    DispatchExecutorService dispatchExecutorService(DispatchExecutor dispatchExecutor) {
        return new DispatchExecutorService(dispatchExecutor);
    }

    @Bean
    DecisionLogAssembler decisionLogAssembler() {
        return new DecisionLogAssembler();
    }

    @Bean
    DecisionLogWriter decisionLogWriter(RouteChainDispatchV2Properties properties) {
        if (properties.getFeedback().getStorageMode() == FeedbackStorageMode.FILE) {
            return new FileDecisionLogWriter(
                    java.nio.file.Path.of(properties.getFeedback().getBaseDir()),
                    properties.getFeedback().getRetention().getMaxFiles());
        }
        return new InMemoryDecisionLogWriter();
    }

    @Bean
    DecisionLogService decisionLogService(RouteChainDispatchV2Properties properties,
                                          DecisionLogAssembler decisionLogAssembler,
                                          DecisionLogWriter decisionLogWriter) {
        return new DecisionLogService(properties, decisionLogAssembler, decisionLogWriter);
    }

    @Bean
    SnapshotBuilder snapshotBuilder() {
        return new SnapshotBuilder();
    }

    @Bean
    SnapshotStore snapshotStore(RouteChainDispatchV2Properties properties) {
        if (properties.getFeedback().getStorageMode() == FeedbackStorageMode.FILE) {
            return new FileSnapshotStore(
                    java.nio.file.Path.of(properties.getFeedback().getBaseDir()),
                    properties.getFeedback().getRetention().getMaxFiles());
        }
        return new InMemorySnapshotStore();
    }

    @Bean
    SnapshotService snapshotService(RouteChainDispatchV2Properties properties,
                                    SnapshotBuilder snapshotBuilder,
                                    SnapshotStore snapshotStore) {
        return new SnapshotService(properties, snapshotBuilder, snapshotStore);
    }

    @Bean
    ReplayStore replayStore(RouteChainDispatchV2Properties properties) {
        if (properties.getFeedback().getStorageMode() == FeedbackStorageMode.FILE) {
            return new FileReplayStore(
                    java.nio.file.Path.of(properties.getFeedback().getBaseDir()),
                    properties.getFeedback().getRetention().getMaxFiles());
        }
        return new InMemoryReplayStore();
    }

    @Bean
    DispatchReplayRecorder dispatchReplayRecorder(RouteChainDispatchV2Properties properties, ReplayStore replayStore) {
        return new DispatchReplayRecorder(properties, replayStore);
    }

    @Bean
    WarmStartManager warmStartManager(RouteChainDispatchV2Properties properties, SnapshotService snapshotService) {
        return new WarmStartManager(properties, snapshotService);
    }

    @Bean
    HotStartManager hotStartManager(RouteChainDispatchV2Properties properties) {
        return new HotStartManager(properties);
    }

    @Bean
    PostDispatchHardeningService postDispatchHardeningService(DispatchReplayRecorder dispatchReplayRecorder,
                                                              DecisionLogService decisionLogService,
                                                              SnapshotService snapshotService,
                                                              HotStartManager hotStartManager) {
        return new PostDispatchHardeningService(
                dispatchReplayRecorder,
                decisionLogService,
                snapshotService,
                hotStartManager);
    }

    @Bean
    DispatchV2Core dispatchV2Core(DispatchEtaContextService dispatchEtaContextService,
                                  DispatchPairClusterService dispatchPairClusterService,
                                  DispatchBundleStageService dispatchBundleStageService,
                                  DispatchRouteCandidateService dispatchRouteCandidateService,
                                  DispatchRouteProposalService dispatchRouteProposalService,
                                  DispatchScenarioService dispatchScenarioService,
                                  DispatchSelectorService dispatchSelectorService,
                                  DispatchExecutorService dispatchExecutorService,
                                  WarmStartManager warmStartManager,
                                  PostDispatchHardeningService postDispatchHardeningService) {
        return new DispatchV2Core(
                dispatchEtaContextService,
                dispatchPairClusterService,
                dispatchBundleStageService,
                dispatchRouteCandidateService,
                dispatchRouteProposalService,
                dispatchScenarioService,
                dispatchSelectorService,
                dispatchExecutorService,
                warmStartManager,
                postDispatchHardeningService);
    }

    @Bean
    DispatchReplayLoader dispatchReplayLoader(DispatchReplayRecorder dispatchReplayRecorder,
                                              DecisionLogService decisionLogService,
                                              SnapshotService snapshotService) {
        return new DispatchReplayLoader(dispatchReplayRecorder, decisionLogService, snapshotService);
    }

    @Bean
    DispatchReplayComparator dispatchReplayComparator() {
        return new DispatchReplayComparator();
    }

    @Bean
    DispatchReplayRunner dispatchReplayRunner(DispatchV2Core dispatchV2Core,
                                              DispatchReplayLoader dispatchReplayLoader,
                                              DispatchReplayComparator dispatchReplayComparator) {
        return new DispatchReplayRunner(dispatchV2Core, dispatchReplayLoader, dispatchReplayComparator);
    }

    @Bean
    DispatchV2CompatibleCore dispatchV2CompatibleCore(RouteChainDispatchV2Properties properties, DispatchV2Core dispatchV2Core) {
        return new DispatchV2CompatibleCore(properties, dispatchV2Core);
    }
}
