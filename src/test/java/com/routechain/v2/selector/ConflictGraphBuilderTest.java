package com.routechain.v2.selector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConflictGraphBuilderTest {

    @Test
    void emitsDeterministicConflictsForOrdersAndDriversOnlyWhereNeeded() {
        ConflictGraph graph = new ConflictGraphBuilder().build(List.of(
                SelectorTestFixtures.candidate("proposal-a", "bundle-a", "driver-1", List.of("order-1", "order-2"), 0.8, 0.7, 0.9, true),
                SelectorTestFixtures.candidate("proposal-b", "bundle-b", "driver-1", List.of("order-2", "order-3"), 0.7, 0.6, 0.8, true),
                SelectorTestFixtures.candidate("proposal-c", "bundle-c", "driver-3", List.of("order-4"), 0.6, 0.5, 0.7, true)));

        assertTrue(graph.edges().stream().anyMatch(edge -> edge.leftProposalId().equals("proposal-a")
                && edge.rightProposalId().equals("proposal-b")
                && edge.reason() == ConflictReason.DRIVER_OVERLAP));
        assertTrue(graph.edges().stream().anyMatch(edge -> edge.leftProposalId().equals("proposal-a")
                && edge.rightProposalId().equals("proposal-b")
                && edge.reason() == ConflictReason.ORDER_OVERLAP));
        assertFalse(graph.edges().stream().anyMatch(edge -> edge.leftProposalId().equals("proposal-c")
                || edge.rightProposalId().equals("proposal-c")));
        assertEquals(2, graph.conflictEdgeCount());
    }
}
