package com.routechain.v2.route;

import com.routechain.domain.Driver;
import com.routechain.v2.context.EtaEstimate;

public record CandidateDriverMatch(
        Driver driver,
        int rank,
        EtaEstimate reachEta,
        double score) {
}
