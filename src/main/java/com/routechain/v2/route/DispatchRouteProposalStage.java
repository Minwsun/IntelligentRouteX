package com.routechain.v2.route;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.HotStartReuseSummary;

import java.util.List;

public record DispatchRouteProposalStage(
        String schemaVersion,
        List<RouteProposal> routeProposals,
        RouteProposalSummary routeProposalSummary,
        HotStartReuseSummary hotStartReuseSummary,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}
