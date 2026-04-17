package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.route.RouteProposal;

import java.util.List;

public record RouteProposalTupleReuseEntry(
        String schemaVersion,
        String bundleId,
        String anchorOrderId,
        String driverId,
        String tupleSignature,
        List<RouteProposal> routeProposals) implements SchemaVersioned {
}
