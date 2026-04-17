package com.routechain.v2.integration;

import java.util.List;

public record GreedRlSequenceProposal(
        List<String> stopOrder,
        double sequenceScore,
        List<String> traceReasons) {
}
