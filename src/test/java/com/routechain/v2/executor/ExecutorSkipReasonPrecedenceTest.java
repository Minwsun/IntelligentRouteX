package com.routechain.v2.executor;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.selector.DispatchSelectorStage;
import com.routechain.v2.selector.SelectedProposal;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorSkipReasonPrecedenceTest {

    @Test
    void missingContextSkipHappensBeforeConflictValidation() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        var pairClusterStage = RouteTestFixtures.pairClusterStage(properties);
        var bundleStage = RouteTestFixtures.bundleStage(properties, pairClusterStage);
        var routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchSelectorStage selectorStage = RouteTestFixtures.selectorStage(properties);
        DispatchCandidateContext context = new DispatchCandidateContext(
                pairClusterStage.bufferedOrderWindow().orders(),
                RouteTestFixtures.request().availableDrivers(),
                pairClusterStage,
                bundleStage);
        SelectedProposalResolver resolver = new SelectedProposalResolver();
        ExecutionConflictValidator validator = new ExecutionConflictValidator();
        SelectedProposal selectedProposal = selectorStage.globalSelectionResult().selectedProposals().getFirst();

        SelectedProposalResolveResult unresolved = resolver.resolve(
                selectedProposal,
                Map.of(),
                Map.of(),
                routeCandidateStage,
                context);
        ExecutionConflictValidationResult validationResult = validator.validate(java.util.List.of());

        assertTrue(unresolved.degradeReasons().contains("executor-missing-selected-proposal-context"));
        assertFalse(validationResult.degradeReasons().contains("executor-conflict-validation-failed"));
    }
}
