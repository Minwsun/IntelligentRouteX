package com.routechain.v2.feedback;

import com.routechain.domain.Order;
import com.routechain.v2.DispatchPipelineExecution;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.cluster.BufferedOrderWindow;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.PickupAnchor;
import com.routechain.v2.route.RouteProposal;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ReuseStateBuilder {

    public DispatchRuntimeReuseState build(DispatchV2Request request, DispatchPipelineExecution execution) {
        var result = execution.result();
        Map<String, BundleCandidate> bundlesById = result.bundleCandidates().stream()
                .collect(Collectors.toMap(BundleCandidate::bundleId, bundle -> bundle, (left, right) -> left));
        Map<String, PickupAnchor> anchorsByKey = result.pickupAnchors().stream()
                .collect(Collectors.toMap(anchor -> tupleKey(anchor.bundleId(), anchor.anchorOrderId(), null), anchor -> anchor, (left, right) -> left));
        Map<String, DriverCandidate> driversByKey = result.driverCandidates().stream()
                .collect(Collectors.toMap(driver -> tupleKey(driver.bundleId(), driver.anchorOrderId(), driver.driverId()), driver -> driver, (left, right) -> left));

        List<RouteProposalTupleReuseEntry> routeProposalTuples = result.routeProposals().stream()
                .collect(Collectors.groupingBy(
                        proposal -> tupleKey(proposal.bundleId(), proposal.anchorOrderId(), proposal.driverId()),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> toTupleEntry(entry.getValue(), bundlesById, anchorsByKey, driversByKey))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RouteProposalTupleReuseEntry::bundleId)
                        .thenComparing(RouteProposalTupleReuseEntry::anchorOrderId)
                        .thenComparing(RouteProposalTupleReuseEntry::driverId))
                .toList();

        return new DispatchRuntimeReuseState(
                "dispatch-runtime-reuse-state/v1",
                result.traceId() + "-reuse-state-" + request.decisionTime().toEpochMilli(),
                result.traceId(),
                request.decisionTime(),
                etaContextSignature(result.etaContext()),
                bufferedOrderWindowSignature(result.bufferedOrderWindow()),
                clusterSignatures(result.microClusters()),
                bundleSignatures(result.bundleCandidates()),
                execution.pairClusterStage().pairSimilarityGraph(),
                execution.pairClusterStage().pairGraphSummary(),
                execution.pairClusterStage().microClusters(),
                execution.pairClusterStage().microClusterSummary(),
                execution.pairClusterStage().mlStageMetadata(),
                execution.pairClusterStage().degradeReasons(),
                execution.bundleStage().boundaryExpansions(),
                execution.bundleStage().boundaryExpansionSummary(),
                execution.bundleStage().bundleCandidates(),
                execution.bundleStage().bundlePoolSummary(),
                execution.bundleStage().mlStageMetadata(),
                execution.bundleStage().degradeReasons(),
                execution.routeProposalStage().routeProposals(),
                execution.routeProposalStage().routeProposalSummary(),
                routeProposalTuples,
                execution.routeProposalStage().mlStageMetadata(),
                execution.routeProposalStage().degradeReasons(),
                result.stageLatencies(),
                result.degradeReasons());
    }

    private RouteProposalTupleReuseEntry toTupleEntry(List<RouteProposal> proposals,
                                                      Map<String, BundleCandidate> bundlesById,
                                                      Map<String, PickupAnchor> anchorsByKey,
                                                      Map<String, DriverCandidate> driversByKey) {
        if (proposals == null || proposals.isEmpty()) {
            return null;
        }
        RouteProposal seedProposal = proposals.getFirst();
        String bundleId = seedProposal.bundleId();
        String anchorOrderId = seedProposal.anchorOrderId();
        String driverId = seedProposal.driverId();
        BundleCandidate bundle = bundlesById.get(bundleId);
        PickupAnchor anchor = anchorsByKey.get(tupleKey(bundleId, anchorOrderId, null));
        DriverCandidate driver = driversByKey.get(tupleKey(bundleId, anchorOrderId, driverId));
        if (bundle == null || anchor == null || driver == null) {
            return null;
        }
        return new RouteProposalTupleReuseEntry(
                "route-proposal-tuple-reuse-entry/v1",
                bundleId,
                anchorOrderId,
                driverId,
                routeProposalTupleSignature(bundle, anchor, driver),
                List.copyOf(proposals.stream()
                        .sorted(Comparator.comparing(RouteProposal::proposalId))
                        .toList()));
    }

    public static String etaContextSignature(EtaContext etaContext) {
        if (etaContext == null) {
            return "eta:none";
        }
        return "eta|%d|%s|%s|%s|%s|%s|%s".formatted(
                etaContext.sampledLegCount(),
                doubleToken(etaContext.averageEtaMinutes()),
                doubleToken(etaContext.maxEtaMinutes()),
                doubleToken(etaContext.averageUncertainty()),
                Boolean.toString(etaContext.trafficBadSignal()),
                Boolean.toString(etaContext.weatherBadSignal()),
                etaContext.refineSource() == null ? "" : etaContext.refineSource());
    }

    public static String bufferedOrderWindowSignature(BufferedOrderWindow bufferedOrderWindow) {
        if (bufferedOrderWindow == null) {
            return "buffer:none";
        }
        return "buffer|%d|%d|%s".formatted(
                bufferedOrderWindow.holdWindowMs(),
                bufferedOrderWindow.orderCount(),
                bufferedOrderWindow.orders().stream()
                        .sorted(Comparator.comparing(Order::orderId))
                        .map(order -> "%s@%s@%s".formatted(order.orderId(), order.readyAt(), order.urgent()))
                        .collect(Collectors.joining(",")));
    }

    public static List<String> clusterSignatures(List<MicroCluster> microClusters) {
        return microClusters.stream()
                .map(cluster -> cluster.clusterId()
                        + "|" + cluster.corridorSignature()
                        + "|" + String.join(",", cluster.orderIds()))
                .sorted()
                .toList();
    }

    public static List<String> bundleSignatures(List<BundleCandidate> bundles) {
        return bundles.stream()
                .map(bundle -> bundle.bundleId()
                        + "|" + bundle.orderSetSignature()
                        + "|" + bundle.clusterId())
                .sorted()
                .toList();
    }

    public static String routeProposalTupleSignature(BundleCandidate bundle,
                                                     PickupAnchor pickupAnchor,
                                                     DriverCandidate driverCandidate) {
        return "%s|%s|%s|%s|%s|%s".formatted(
                bundle.bundleId(),
                pickupAnchor.anchorOrderId(),
                driverCandidate.driverId(),
                bundle.orderSetSignature(),
                doubleToken(driverCandidate.rerankScore()),
                doubleToken(pickupAnchor.score()));
    }

    public static String tupleKey(String bundleId, String anchorOrderId, String driverId) {
        return "%s|%s|%s".formatted(
                bundleId == null ? "" : bundleId,
                anchorOrderId == null ? "" : anchorOrderId,
                driverId == null ? "" : driverId);
    }

    private static String doubleToken(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
