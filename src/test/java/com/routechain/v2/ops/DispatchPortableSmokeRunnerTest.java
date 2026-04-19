package com.routechain.v2.ops;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.executor.DispatchAssignment;
import com.routechain.v2.executor.ExecutionActionType;
import com.routechain.v2.route.RouteProposalSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchPortableSmokeRunnerTest {

    @Test
    void canonicalRequestMatchesAssignablePortableSmokeShape() {
        DispatchV2Request request = DispatchPortableSmokeRunner.canonicalRequest("trace-portable-smoke");

        assertEquals("trace-portable-smoke", request.traceId());
        assertEquals(3, request.openOrders().size());
        assertEquals(3, request.availableDrivers().size());
        assertEquals("order-1", request.openOrders().getFirst().orderId());
        assertEquals("driver-1", request.availableDrivers().getFirst().driverId());
        assertEquals(DispatchPortableSmokeRunner.EXPECTED_DECISION_STAGES.size(), 12);
    }

    @Test
    void conflictSummaryFlagsDuplicateDriversAndOrders() {
        List<DispatchAssignment> assignments = List.of(
                assignment("assignment-1", "driver-1", List.of("order-1", "order-2")),
                assignment("assignment-2", "driver-1", List.of("order-2")));

        var summary = DispatchPortableSmokeRunner.assignmentConflictSummary(assignments);

        assertFalse(summary.conflictFree());
        assertTrue(summary.conflictReasons().contains("duplicate-driver:driver-1"));
        assertTrue(summary.conflictReasons().contains("duplicate-order:order-2"));
    }

    @Test
    void conflictSummaryPassesForUniqueAssignments() {
        List<DispatchAssignment> assignments = List.of(
                assignment("assignment-1", "driver-1", List.of("order-1")),
                assignment("assignment-2", "driver-2", List.of("order-2")));

        var summary = DispatchPortableSmokeRunner.assignmentConflictSummary(assignments);

        assertTrue(summary.conflictFree());
        assertTrue(summary.conflictReasons().isEmpty());
    }

    private static DispatchAssignment assignment(String assignmentId, String driverId, List<String> orderIds) {
        return new DispatchAssignment(
                "dispatch-assignment/v1",
                assignmentId,
                "proposal-" + assignmentId,
                "bundle-1",
                orderIds.getFirst(),
                driverId,
                orderIds,
                orderIds,
                ExecutionActionType.ASSIGN_DRIVER,
                RouteProposalSource.HEURISTIC_FAST,
                1,
                1.0,
                1.0,
                5.0,
                10.0,
                1.0,
                "cluster-1",
                false,
                Instant.parse("2026-04-16T12:00:00Z"),
                Instant.parse("2026-04-16T12:10:00Z"),
                List.of(),
                List.of());
    }
}
