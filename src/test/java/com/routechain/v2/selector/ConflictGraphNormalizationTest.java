package com.routechain.v2.selector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConflictGraphNormalizationTest {

    @Test
    void normalizesEveryEdgeAndDoesNotEmitReverseDuplicates() {
        ConflictGraph graph = new ConflictGraphBuilder().build(List.of(
                SelectorTestFixtures.candidate("proposal-z", "bundle-a", "driver-1", List.of("order-1"), 0.8, 0.7, 0.9, true),
                SelectorTestFixtures.candidate("proposal-a", "bundle-b", "driver-1", List.of("order-2"), 0.7, 0.6, 0.8, true)));

        assertTrue(graph.edges().stream().allMatch(edge -> edge.leftProposalId().compareTo(edge.rightProposalId()) < 0));
        Set<String> normalizedPairs = graph.edges().stream()
                .map(edge -> edge.leftProposalId() + "|" + edge.rightProposalId() + "|" + edge.reason().name())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(graph.edges().size(), normalizedPairs.size());
    }
}
