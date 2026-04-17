package com.routechain.v2.selector;

public record ConflictEdge(
        String leftProposalId,
        String rightProposalId,
        ConflictReason reason) {
}
