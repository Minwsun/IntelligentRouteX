package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record RouteProposalSummary(
        String schemaVersion,
        int driverCandidateCount,
        int proposalTupleCount,
        int proposalCount,
        int retainedProposalCount,
        Map<RouteProposalSource, Integer> sourceCounts,
        List<String> degradeReasons) implements SchemaVersioned {

    public static RouteProposalSummary empty() {
        return new RouteProposalSummary(
                "route-proposal-summary/v1",
                0,
                0,
                0,
                0,
                new EnumMap<>(RouteProposalSource.class),
                List.of());
    }
}
