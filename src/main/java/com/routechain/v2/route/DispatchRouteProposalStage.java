package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchRouteProposalStage(
        String schemaVersion,
        List<RouteProposal> routeProposals,
        RouteProposalSummary routeProposalSummary,
        List<String> degradeReasons) implements SchemaVersioned {
}
