package com.routechain.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DualPenaltyControllerTest {

    @Test
    void rollingWindowShouldNotOverreactToSingleBadOutcome() {
        DualPenaltyController controller = new DualPenaltyController();
        controller.configureWindow(20);

        double initialLatePenalty = controller.currentPenalties().get("lambda_late");
        controller.recordOutcome(new OutcomeVector(0.10, 0.80, 0.82, 0.72, 0.70, 0.68, 0.92));
        double afterOneBadOutcome = controller.currentPenalties().get("lambda_late");

        for (int i = 0; i < 8; i++) {
            controller.recordOutcome(new OutcomeVector(0.92, 1.0, 0.86, 0.78, 0.80, 0.82, 0.96));
        }
        controller.decay();
        double afterRecovery = controller.currentPenalties().get("lambda_late");

        assertTrue(afterOneBadOutcome < initialLatePenalty + 0.05,
                "A single bad outcome should not cause a large penalty spike");
        assertTrue(afterRecovery <= afterOneBadOutcome,
                "Rolling-window penalties should relax once good outcomes dominate the window");
    }
}
