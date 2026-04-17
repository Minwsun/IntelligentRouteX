package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionLogAssemblerTest {

    @Test
    void assemblesSummariesAndIdsFromDispatchResultWithoutRecomputing() {
        DispatchV2Result result = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults())
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        DecisionLogRecord record = new DecisionLogAssembler()
                .assemble(TestDispatchV2Factory.requestWithOrdersAndDriver(), result);

        assertEquals(result.traceId(), record.traceId());
        assertEquals(TestDispatchV2Factory.requestWithOrdersAndDriver().decisionTime(), record.decisionTime());
        assertEquals(result.etaContext(), record.etaSummary());
        assertEquals(result.routeProposalSummary(), record.routeProposalSummary());
        assertEquals(result.globalSelectorSummary(), record.globalSelectorSummary());
        assertEquals(result.dispatchExecutionSummary(), record.dispatchExecutionSummary());
        assertEquals(result.mlStageMetadata(), record.mlStageMetadata());
        assertEquals(
                result.globalSelectionResult().selectedProposals().stream().map(selectedProposal -> selectedProposal.proposalId()).toList(),
                record.selectedProposalIds());
        assertEquals(
                result.assignments().stream().map(assignment -> assignment.assignmentId()).toList(),
                record.executedAssignmentIds());
    }
}
