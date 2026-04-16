package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record RouteProposal(
        String schemaVersion,
        String proposalId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        RouteProposalSource source,
        List<String> stopOrder,
        double projectedPickupEtaMinutes,
        double projectedCompletionEtaMinutes,
        double routeValue,
        boolean feasible,
        List<String> reasons,
        List<String> degradeReasons) implements SchemaVersioned {
}
