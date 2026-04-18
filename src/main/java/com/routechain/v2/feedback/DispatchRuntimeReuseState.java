package com.routechain.v2.feedback;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.bundle.BoundaryExpansion;
import com.routechain.v2.bundle.BoundaryExpansionSummary;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.bundle.BundlePoolSummary;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.MicroClusterSummary;
import com.routechain.v2.cluster.PairGraphSummary;
import com.routechain.v2.cluster.PairSimilarityGraph;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteProposalSummary;

import java.time.Instant;
import java.util.List;

public record DispatchRuntimeReuseState(
        String schemaVersion,
        String reuseStateId,
        String traceId,
        Instant createdAt,
        String etaContextSignature,
        String bufferedOrderWindowSignature,
        List<String> clusterSignatures,
        List<String> bundleSignatures,
        PairSimilarityGraph pairSimilarityGraph,
        PairGraphSummary pairGraphSummary,
        List<MicroCluster> microClusters,
        MicroClusterSummary microClusterSummary,
        List<MlStageMetadata> pairClusterMlStageMetadata,
        List<String> pairClusterDegradeReasons,
        List<BoundaryExpansion> boundaryExpansions,
        BoundaryExpansionSummary boundaryExpansionSummary,
        List<BundleCandidate> bundleCandidates,
        BundlePoolSummary bundlePoolSummary,
        List<MlStageMetadata> bundleMlStageMetadata,
        List<String> bundleDegradeReasons,
        List<RouteProposal> routeProposals,
        RouteProposalSummary routeProposalSummary,
        List<RouteProposalTupleReuseEntry> routeProposalTuples,
        List<MlStageMetadata> routeProposalMlStageMetadata,
        List<String> routeProposalDegradeReasons,
        List<DispatchStageLatency> stageLatencies,
        List<String> degradeReasons) implements SchemaVersioned {
}
