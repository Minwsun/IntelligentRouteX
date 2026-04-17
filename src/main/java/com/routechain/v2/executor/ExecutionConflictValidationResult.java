package com.routechain.v2.executor;

import java.util.List;

record ExecutionConflictValidationResult(
        List<ResolvedSelectedProposal> acceptedProposals,
        DispatchExecutionTrace trace,
        List<String> degradeReasons) {
}
