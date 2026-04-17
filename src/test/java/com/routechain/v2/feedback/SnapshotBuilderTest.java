package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotBuilderTest {

    @Test
    void snapshotContainsExpectedIdsSignaturesAndObjectiveValue() {
        DispatchV2Request request = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result result = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults()).dispatch(request);

        DispatchRuntimeSnapshot snapshot = new SnapshotBuilder().build(request, result);

        assertEquals(result.traceId(), snapshot.traceId());
        assertEquals(result.decisionStages(), snapshot.decisionStages());
        assertEquals(result.globalSelectionResult().objectiveValue(), snapshot.selectorObjectiveValue());
        assertEquals(
                result.globalSelectionResult().selectedProposals().stream().map(selectedProposal -> selectedProposal.proposalId()).toList(),
                snapshot.selectedProposalIds());
        assertEquals(
                result.assignments().stream().map(assignment -> assignment.assignmentId()).toList(),
                snapshot.executedAssignmentIds());
    }

    @Test
    void snapshotIsStableForSameInput() {
        DispatchV2Request request = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result result = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults()).dispatch(request);
        SnapshotBuilder snapshotBuilder = new SnapshotBuilder();

        assertEquals(snapshotBuilder.build(request, result), snapshotBuilder.build(request, result));
    }
}
