package com.routechain.v2.selector;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.route.RouteProposalSource;

import java.util.List;

public record SelectorCandidate(
        String schemaVersion,
        String proposalId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        List<String> orderIds,
        double robustUtility,
        double routeValue,
        RouteProposalSource source,
        String clusterId,
        boolean boundaryCross,
        double selectionScore,
        boolean feasible,
        List<String> reasons,
        List<String> degradeReasons) implements SchemaVersioned {
}
