package com.routechain.v2.executor;

import java.util.List;
import java.util.Optional;

record SelectedProposalResolveResult(
        Optional<ResolvedSelectedProposal> resolvedProposal,
        DispatchExecutionTrace trace,
        List<String> degradeReasons) {
}
