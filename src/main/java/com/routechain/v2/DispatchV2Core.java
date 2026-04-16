package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.infra.PlatformRuntimeBootstrap;
import com.routechain.v2.bundle.BundleBuilder;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.cluster.BoundaryExpansion;
import com.routechain.v2.cluster.BoundaryMerge;
import com.routechain.v2.cluster.BufferedOrderWindow;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.MicroClusterer;
import com.routechain.v2.cluster.OrderBuffer;
import com.routechain.v2.cluster.PairSimilarityGraph;
import com.routechain.v2.cluster.PairSimilarityScorer;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.integration.DispatchV2ModelSidecarClient;
import com.routechain.v2.integration.GreedRLAdapter;
import com.routechain.v2.integration.RouteFinderAdapter;
import com.routechain.v2.route.CandidateDriverMatch;
import com.routechain.v2.route.CandidateDriverRanker;
import com.routechain.v2.route.PickupAnchor;
import com.routechain.v2.route.PickupAnchorSelector;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteProposalEngine;
import com.routechain.v2.route.RouteValueScorer;
import com.routechain.v2.route.ScenarioEvaluator;
import com.routechain.v2.selector.GlobalSelector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DispatchV2Core {
    private final RouteChainDispatchV2Properties properties;
    private final EtaService etaService;
    private final OrderBuffer orderBuffer;
    private final PairSimilarityScorer pairSimilarityScorer;
    private final MicroClusterer microClusterer;
    private final BoundaryMerge boundaryMerge;
    private final BundleBuilder bundleBuilder;
    private final PickupAnchorSelector pickupAnchorSelector;
    private final CandidateDriverRanker candidateDriverRanker;
    private final RouteProposalEngine routeProposalEngine;
    private final ScenarioEvaluator scenarioEvaluator;
    private final RouteValueScorer routeValueScorer;
    private final GlobalSelector globalSelector;
    private final Set<String> incumbentSignatures = new LinkedHashSet<>();

    public DispatchV2Core(RouteChainDispatchV2Properties properties) {
        this.properties = properties == null ? RouteChainDispatchV2Properties.defaults() : properties;
        this.etaService = new EtaService(PlatformRuntimeBootstrap.getRoadGraphProvider(), this.properties);
        this.orderBuffer = new OrderBuffer(this.properties.getBuffer());
        this.pairSimilarityScorer = new PairSimilarityScorer(etaService);
        this.microClusterer = new MicroClusterer(this.properties.getCluster());
        this.boundaryMerge = new BoundaryMerge(this.properties.getCluster().getMaxNeighborClusters());
        this.bundleBuilder = new BundleBuilder(this.properties.getBundle());
        DispatchV2ModelSidecarClient sidecarClient = new DispatchV2ModelSidecarClient(this.properties.getSidecar());
        this.pickupAnchorSelector = new PickupAnchorSelector(this.properties.getCandidate(), etaService);
        this.candidateDriverRanker = new CandidateDriverRanker(this.properties.getCandidate(), etaService);
        this.routeProposalEngine = new RouteProposalEngine(
                this.properties.getCandidate(),
                etaService,
                new GreedRLAdapter(sidecarClient),
                new RouteFinderAdapter(sidecarClient));
        this.scenarioEvaluator = new ScenarioEvaluator(this.properties);
        this.routeValueScorer = new RouteValueScorer();
        this.globalSelector = new GlobalSelector();
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        long started = System.nanoTime();
        if (request == null || request.openOrders() == null || request.openOrders().isEmpty()
                || request.availableDrivers() == null || request.availableDrivers().isEmpty()) {
            return DispatchV2Result.empty();
        }

        BufferedOrderWindow window = orderBuffer.buffer(request.openOrders(), request.decisionTime());
        List<Order> releasedOrders = window.releasedOrders();
        if (releasedOrders.isEmpty()) {
            return DispatchV2Result.empty();
        }

        List<OrderSimilarity> similarities = pairSimilarityScorer.scorePairs(releasedOrders, request);
        PairSimilarityGraph similarityGraph = new PairSimilarityGraph(similarities);
        List<MicroCluster> clusters = microClusterer.cluster(window, similarityGraph);
        List<BoundaryExpansion> boundaryExpansions = boundaryMerge.expand(clusters, similarityGraph);
        List<BundleCandidate> bundles = bundleBuilder.build(clusters, boundaryExpansions, similarityGraph);

        if (bundles.isEmpty()) {
            bundles = releasedOrders.stream()
                    .map(order -> new BundleCandidate(
                            "bundle-single-" + order.getId(),
                            "cluster-single-" + order.getPickupRegionId(),
                            null,
                            List.of(order),
                            new BundleScore(1.0, 1.0, 0.9, 0.9, 1.0, 0.7, 0.8, 0.88)))
                    .toList();
        }

        List<DispatchV2PlanCandidate> routePool = new ArrayList<>();
        for (BundleCandidate bundle : bundles) {
            List<PickupAnchor> anchors = pickupAnchorSelector.select(
                    bundle,
                    request.availableDrivers(),
                    request.decisionTime(),
                    request.weatherProfile(),
                    request.trafficIntensity());
            for (PickupAnchor anchor : anchors) {
                List<CandidateDriverMatch> driverMatches = candidateDriverRanker.rank(
                        bundle,
                        anchor,
                        request.availableDrivers(),
                        request.decisionTime(),
                        request.weatherProfile(),
                        request.trafficIntensity());
                for (CandidateDriverMatch driverMatch : driverMatches) {
                    List<RouteProposal> proposals = routeProposalEngine.propose(
                            bundle,
                            anchor,
                            driverMatch,
                            request.decisionTime(),
                            request.weatherProfile(),
                            request.trafficIntensity(),
                            window.windowId());
                    for (RouteProposal proposal : proposals) {
                        RouteProposal evaluated = scenarioEvaluator.evaluate(
                                proposal,
                                request.weatherProfile(),
                                request.trafficIntensity());
                        DispatchV2PlanCandidate candidate = routeValueScorer.score(
                                evaluated,
                                request.regions(),
                                incumbentSignatures);
                        routePool.add(new DispatchV2PlanCandidate(
                                candidate.candidateId(),
                                candidate.plan(),
                                bundle.bundleScore(),
                                candidate.robustUtility(),
                                candidate.globalValue(),
                                candidate.summary()));
                    }
                }
            }
        }

        List<DispatchV2PlanCandidate> selectedRoutes = globalSelector.select(routePool);
        incumbentSignatures.clear();
        selectedRoutes.stream()
                .map(candidate -> routeValueScorer.signature(new RouteProposal(
                        candidate.candidateId(),
                        candidate.plan().getRouteProposalSource(),
                        candidate.plan(),
                        candidate.plan().getTotalScore(),
                        candidate.robustUtility())))
                .forEach(incumbentSignatures::add);

        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        return new DispatchV2Result(
                List.copyOf(routePool),
                List.copyOf(selectedRoutes),
                List.copyOf(similarities),
                List.copyOf(clusters),
                List.copyOf(boundaryExpansions),
                latencyMs);
    }
}
