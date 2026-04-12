package com.routechain.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactDecisionLedgerTest {

    @Test
    void shouldEmitAcceptAndTerminalStagesBeforePostDropResolution() {
        CompactDecisionLedger ledger = new CompactDecisionLedger();
        WeightSnapshot snapshotBefore = new AdaptiveWeightEngine().snapshot();
        PlanFeatureVector features = new PlanFeatureVector(0.80, 0.16, 0.72, 0.65, 0.68, 0.74, 0.20, 0.08);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                0.61,
                0.04,
                0.57,
                Map.of("on_time_probability", 0.32, "last_drop_landing", 0.15),
                Map.of("lambda_empty_after", 0.04));

        ledger.recordDecision(
                "trace-stage",
                "driver-stage",
                "bundle-stage",
                CompactPlanType.SINGLE_LOCAL,
                List.of("order-stage"),
                features,
                breakdown,
                snapshotBefore,
                Instant.parse("2026-04-11T05:00:00Z"),
                12.0,
                0.9,
                42000.0,
                0.76,
                0.68,
                0.8,
                2.4,
                0.08,
                0.80,
                4.8);

        CompactDecisionResolution accepted = ledger.recordAccepted("trace-stage", Instant.parse("2026-04-11T05:01:00Z"));
        CompactDecisionResolution terminal = ledger.recordOrderDelivered(
                "trace-stage",
                "order-stage",
                true,
                42000.0,
                13.0,
                Instant.parse("2026-04-11T05:10:00Z"));

        assertNotNull(accepted);
        assertEquals(DecisionOutcomeStage.AFTER_ACCEPT, accepted.resolvedSample().outcomeStage());
        assertTrue(Double.isNaN(accepted.resolvedSample().actualEtaMinutes()));
        assertNotNull(terminal);
        assertEquals(DecisionOutcomeStage.AFTER_TERMINAL, terminal.resolvedSample().outcomeStage());
        assertEquals(13.0, terminal.resolvedSample().actualEtaMinutes(), 1e-9);
        assertNull(ledger.recordAccepted("trace-stage", Instant.parse("2026-04-11T05:02:00Z")));
    }

    @Test
    void shouldResolveDeliveredPlanAfterPostDropHitOnlyOnce() {
        CompactDecisionLedger ledger = new CompactDecisionLedger();
        AdaptiveWeightEngine weightEngine = new AdaptiveWeightEngine();
        WeightSnapshot snapshotBefore = weightEngine.snapshot();
        PlanFeatureVector features = new PlanFeatureVector(0.80, 0.16, 0.72, 0.65, 0.68, 0.74, 0.20, 0.08);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                0.61,
                0.04,
                0.57,
                Map.of("on_time_probability", 0.32, "last_drop_landing", 0.15),
                Map.of("lambda_empty_after", 0.04));

        ledger.recordDecision(
                "trace-1",
                "driver-1",
                "bundle-1",
                CompactPlanType.SINGLE_LOCAL,
                List.of("order-1"),
                features,
                breakdown,
                snapshotBefore,
                Instant.parse("2026-04-11T05:00:00Z"),
                12.0,
                0.9,
                42000.0,
                0.76,
                0.68,
                0.8,
                2.4,
                0.08,
                0.80,
                4.8);

        ledger.recordAccepted("trace-1", Instant.parse("2026-04-11T05:01:00Z"));
        ledger.recordOrderDelivered("trace-1", "order-1", true, 42000.0, 13.0, Instant.parse("2026-04-11T05:10:00Z"));
        ledger.markDriverIdle("driver-1", 10L, Instant.parse("2026-04-11T05:10:00Z"), new com.routechain.domain.GeoPoint(10.77, 106.70));

        CompactDecisionResolution resolution = ledger.recordPostDropHit(
                "driver-1",
                12L,
                Instant.parse("2026-04-11T05:11:00Z"),
                new com.routechain.domain.GeoPoint(10.775, 106.705));

        assertNotNull(resolution);
        assertEquals("trace-1", resolution.decisionLog().decisionId());
        assertEquals(DecisionOutcomeStage.AFTER_POST_DROP_WINDOW, resolution.resolvedSample().outcomeStage());
        assertEquals(13.0, resolution.resolvedSample().actualEtaMinutes(), 1e-9);
        assertTrue(resolution.resolvedSample().actualPostCompletionEmptyKm() >= 0.0);
        assertTrue(resolution.postDropHit());
        assertEquals(1.0, resolution.outcomeVector().completion(), 1e-9);
        assertEquals(1.0, resolution.outcomeVector().onTime(), 1e-9);
        assertNull(ledger.recordPostDropHit("driver-1", 13L, Instant.parse("2026-04-11T05:12:00Z"), null));
    }

    @Test
    void shouldExpireIdleDecisionWhenNoPostDropHitArrives() {
        CompactDecisionLedger ledger = new CompactDecisionLedger();
        WeightSnapshot snapshotBefore = new AdaptiveWeightEngine().snapshot();
        PlanFeatureVector features = new PlanFeatureVector(0.74, 0.20, 0.58, 0.52, 0.60, 0.62, 0.30, 0.12);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.OFFPEAK_LOW_DENSITY,
                0.40,
                0.02,
                0.38,
                Map.of("delivery_corridor_quality", 0.14),
                Map.of());

        ledger.recordDecision(
                "trace-2",
                "driver-2",
                "bundle-2",
                CompactPlanType.FALLBACK_LOCAL,
                List.of("order-2"),
                features,
                breakdown,
                snapshotBefore,
                Instant.parse("2026-04-11T06:00:00Z"),
                18.0,
                1.1,
                38000.0,
                0.48,
                0.24,
                2.4,
                6.5,
                0.12,
                0.74,
                6.0);
        ledger.recordAccepted("trace-2", Instant.parse("2026-04-11T06:01:00Z"));
        ledger.recordOrderCancelled("trace-2", "order-2", Instant.parse("2026-04-11T06:05:00Z"));
        ledger.markDriverIdle("driver-2", 20L, Instant.parse("2026-04-11T06:05:00Z"), new com.routechain.domain.GeoPoint(10.76, 106.71));

        List<CompactDecisionResolution> expired = ledger.expirePostDrop(
                40L,
                Instant.parse("2026-04-11T06:15:00Z"),
                10L);

        assertEquals(1, expired.size());
        assertTrue(expired.get(0).outcomeVector().completion() < 1.0);
        assertTrue(expired.get(0).outcomeVector().postDropQuality() < 1.0);
        assertTrue(Double.isNaN(expired.get(0).resolvedSample().actualPostCompletionEmptyKm()));
    }
}
