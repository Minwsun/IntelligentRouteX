package com.routechain.v2;

import com.routechain.v2.bundle.BoundaryExpansion;
import com.routechain.v2.bundle.BoundaryExpansionSummary;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.bundle.BundlePoolSummary;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.DriverShortlistSummary;
import com.routechain.v2.route.PickupAnchor;
import com.routechain.v2.route.PickupAnchorSummary;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteProposalSummary;
import com.routechain.v2.cluster.BufferedOrderWindow;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.MicroClusterSummary;
import com.routechain.v2.cluster.PairGraphSummary;
import com.routechain.v2.context.EtaStageTrace;
import com.routechain.v2.context.FreshnessMetadata;

import java.util.List;

public record DispatchV2Result(
        String schemaVersion,
        String traceId,
        boolean fallbackUsed,
        String selectedRouteId,
        List<String> decisionStages,
        EtaContext etaContext,
        EtaStageTrace etaStageTrace,
        FreshnessMetadata freshnessMetadata,
        BufferedOrderWindow bufferedOrderWindow,
        PairGraphSummary pairGraphSummary,
        List<MicroCluster> microClusters,
        MicroClusterSummary microClusterSummary,
        List<BoundaryExpansion> boundaryExpansions,
        BoundaryExpansionSummary boundaryExpansionSummary,
        List<BundleCandidate> bundleCandidates,
        BundlePoolSummary bundlePoolSummary,
        List<PickupAnchor> pickupAnchors,
        PickupAnchorSummary pickupAnchorSummary,
        List<DriverCandidate> driverCandidates,
        DriverShortlistSummary driverShortlistSummary,
        List<RouteProposal> routeProposals,
        RouteProposalSummary routeProposalSummary,
        List<String> degradeReasons) implements SchemaVersioned {

    public static DispatchV2Result fallback(String traceId) {
        return new DispatchV2Result(
                "dispatch-v2-result/v1",
                traceId,
                true,
                null,
                List.of("fallback-shell"),
                EtaContext.empty(traceId),
                EtaStageTrace.empty(),
                FreshnessMetadata.empty(),
                new BufferedOrderWindow("buffered-order-window/v1", traceId, null, 0L, List.of(), 0, 0),
                PairGraphSummary.empty(),
                List.of(),
                MicroClusterSummary.empty(),
                List.of(),
                BoundaryExpansionSummary.empty(),
                List.of(),
                BundlePoolSummary.empty(),
                List.of(),
                PickupAnchorSummary.empty(),
                List.of(),
                DriverShortlistSummary.empty(),
                List.of(),
                RouteProposalSummary.empty(),
                List.of("dispatch-v2-disabled"));
    }
}
