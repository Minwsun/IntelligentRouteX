package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.EtaContext;
import com.routechain.v2.cluster.DispatchPairClusterStage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DispatchBundleStageService {
    private final RouteChainDispatchV2Properties properties;
    private final BoundaryCandidateSelector boundaryCandidateSelector;
    private final BoundaryExpansionEngine boundaryExpansionEngine;
    private final BundleSeedGenerator bundleSeedGenerator;
    private final BundleFamilyEnumerator bundleFamilyEnumerator;
    private final BundleValidator bundleValidator;
    private final BundleScorer bundleScorer;
    private final BundleDominancePruner bundleDominancePruner;

    public DispatchBundleStageService(RouteChainDispatchV2Properties properties,
                                      BoundaryCandidateSelector boundaryCandidateSelector,
                                      BoundaryExpansionEngine boundaryExpansionEngine,
                                      BundleSeedGenerator bundleSeedGenerator,
                                      BundleFamilyEnumerator bundleFamilyEnumerator,
                                      BundleValidator bundleValidator,
                                      BundleScorer bundleScorer,
                                      BundleDominancePruner bundleDominancePruner) {
        this.properties = properties;
        this.boundaryCandidateSelector = boundaryCandidateSelector;
        this.boundaryExpansionEngine = boundaryExpansionEngine;
        this.bundleSeedGenerator = bundleSeedGenerator;
        this.bundleFamilyEnumerator = bundleFamilyEnumerator;
        this.bundleValidator = bundleValidator;
        this.bundleScorer = bundleScorer;
        this.bundleDominancePruner = bundleDominancePruner;
    }

    public DispatchBundleStage evaluate(EtaContext etaContext, DispatchPairClusterStage pairClusterStage) {
        Map<String, List<BoundaryCandidate>> boundaryCandidates = boundaryCandidateSelector.select(
                pairClusterStage.bufferedOrderWindow(),
                pairClusterStage.microClusters(),
                pairClusterStage.pairSimilarityGraph());
        List<BoundaryExpansion> boundaryExpansions = pairClusterStage.microClusters().stream()
                .map(cluster -> boundaryExpansionEngine.expand(
                        cluster,
                        boundaryCandidates.getOrDefault(cluster.clusterId(), List.of()),
                        etaContext))
                .toList();

        List<String> degradeReasons = new ArrayList<>();
        BoundaryExpansionSummary boundaryExpansionSummary = summarizeBoundaryExpansions(boundaryExpansions, degradeReasons);
        BundleContext context = new BundleContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                pairClusterStage.pairSimilarityGraph(),
                boundaryExpansions);
        List<BundleSeed> seeds = bundleSeedGenerator.generate(pairClusterStage.microClusters(), context);
        List<BundleCandidate> feasibleCandidates = new ArrayList<>();
        for (BundleSeed seed : seeds) {
            List<BundleCandidate> familyCandidates = bundleFamilyEnumerator.enumerate(seed, context).stream()
                    .map(candidate -> bundleValidator.validate(candidate, context))
                    .filter(BundleCandidate::feasible)
                    .map(candidate -> bundleScorer.score(candidate, context))
                    .sorted(bundleDominancePruner.bundleComparator())
                    .limit(Math.max(1, properties.getBundle().getBeamWidth()))
                    .toList();
            feasibleCandidates.addAll(familyCandidates);
        }
        Map<BundleFamily, Integer> familyCounts = new EnumMap<>(BundleFamily.class);
        feasibleCandidates.forEach(candidate -> familyCounts.merge(candidate.family(), 1, Integer::sum));
        List<BundleCandidate> retained = bundleDominancePruner.prune(feasibleCandidates);
        BundlePoolSummary bundlePoolSummary = new BundlePoolSummary(
                "bundle-pool-summary/v1",
                feasibleCandidates.size(),
                retained.size(),
                familyCounts,
                retained.stream().mapToInt(candidate -> candidate.orderIds().size()).max().orElse(0),
                List.copyOf(degradeReasons));
        return new DispatchBundleStage(
                "dispatch-bundle-stage/v1",
                boundaryExpansions,
                boundaryExpansionSummary,
                retained,
                bundlePoolSummary,
                List.copyOf(degradeReasons));
    }

    private BoundaryExpansionSummary summarizeBoundaryExpansions(List<BoundaryExpansion> expansions, List<String> degradeReasons) {
        int expandedClusterCount = (int) expansions.stream().filter(expansion -> !expansion.acceptedBoundaryOrderIds().isEmpty()).count();
        int acceptedCount = expansions.stream().mapToInt(expansion -> expansion.acceptedBoundaryOrderIds().size()).sum();
        int rejectedCount = expansions.stream().mapToInt(expansion -> expansion.rejectedBoundaryOrderIds().size()).sum();
        degradeReasons.addAll(expansions.stream().flatMap(expansion -> expansion.expansionReasons().stream()).distinct().toList());
        return new BoundaryExpansionSummary(
                "boundary-expansion-summary/v1",
                expansions.size(),
                expandedClusterCount,
                acceptedCount,
                rejectedCount,
                List.copyOf(degradeReasons));
    }
}
