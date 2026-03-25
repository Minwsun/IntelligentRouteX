package com.routechain.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecisionLogBehaviorStatsTest {

    @Test
    void shouldExposeWaitLaunchDowngradeAndRecoveryBehaviorStats() {
        DecisionLog log = new DecisionLog();

        log.log(10L, new double[] {1.0}, new double[15], 0.10, "OMEGA", "hold-1",
                "held for third order, clean-regime 3+ policy");
        log.log(20L, new double[] {1.0}, new double[15], 0.82, "OMEGA", "wave-1",
                "visible pickup-wave 3, launched clean 3-wave, SLA-safe");
        log.log(30L, new double[] {1.0}, new double[15], 0.64, "OMEGA", "down-1",
                "downgraded due to severe stress, compact 2-order wave");
        log.log(40L, new double[] {1.0}, new double[15], 0.78, "OMEGA", "wave-2-AUG",
                "visible pickup-wave 4, extended to 4 on-route, SLA-safe");

        log.recordOutcome("wave-1", 1.25, 50L);
        log.recordOutcome("down-1", -0.40, 60L);
        log.recordOutcome("wave-2-AUG", 0.90, 70L);

        DecisionLog.BehaviorStats stats = log.getBehaviorStats();

        assertEquals(4, stats.totalDecisionCount());
        assertEquals(3, stats.realAssignmentCount());
        assertEquals(1, stats.holdCount());
        assertEquals(1, stats.launchCount());
        assertEquals(1, stats.downgradeCount());
        assertEquals(1, stats.augmentCount());
        assertEquals(3, stats.completedAssignmentCount());
        assertEquals(2, stats.recoveredAssignmentCount());
        assertEquals(1, stats.completedAugmentCount());
        assertEquals(1, stats.recoveredAugmentCount());
        assertEquals(75.0, stats.realAssignmentRate(), 1e-9);
        assertEquals(25.0, stats.holdRate(), 1e-9);
        assertEquals(25.0, stats.launchRate(), 1e-9);
        assertEquals(25.0, stats.downgradeRate(), 1e-9);
        assertEquals(25.0, stats.augmentRate(), 1e-9);
        assertEquals(66.66666666666667, stats.recoveryRate(), 1e-9);
        assertEquals(100.0, stats.augmentRecoveryRate(), 1e-9);
        assertEquals("realAssign=75.0% hold=25.0% launch=25.0% downgrade=25.0% augment=25.0% recover=66.7% augmentRecover=100.0%",
                stats.toSummary());
    }

    @Test
    void recordOutcomeShouldRemainAppendOnlyForCompletedEntries() {
        DecisionLog log = new DecisionLog();
        log.log(10L, new double[] {1.0}, new double[15], 0.82, "OMEGA", "wave-1",
                "visible pickup-wave 3, launched clean 3-wave, SLA-safe");

        log.recordOutcome("wave-1", 1.25, 50L);
        log.recordOutcome("wave-1", 5.00, 90L);

        DecisionLog.DecisionEntry entry = log.getCompletedEntries().get(0);
        assertEquals(1.25, entry.actualReward(), 1e-9);
        assertEquals(50L, entry.completionTick());
    }
}
