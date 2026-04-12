package com.routechain.simulation;

import com.routechain.core.AdaptiveScoreBreakdown;
import com.routechain.core.AdaptiveWeightEngine;
import com.routechain.core.CompactDecisionLedger;
import com.routechain.core.CompactDecisionResolution;
import com.routechain.core.PlanFeatureVector;
import com.routechain.core.RegimeKey;
import com.routechain.core.WeightSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactBundleLifecycleTest {

    @Test
    void shouldResolveTwoOrderBundleLifecycleWithPerOrderOutcomeAccounting() {
        CompactDecisionLedger ledger = new CompactDecisionLedger();
        WeightSnapshot snapshotBefore = new AdaptiveWeightEngine().snapshot();
        PlanFeatureVector features = new PlanFeatureVector(0.82, 0.14, 0.74, 0.60, 0.68, 0.72, 0.18, 0.06);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                0.66,
                0.03,
                0.63,
                Map.of("bundle_efficiency", 0.18, "last_drop_landing", 0.16),
                Map.of("lambda_empty_after", 0.03));

        ledger.recordDecision(
                "trace-bundle",
                "driver-bundle",
                "bundle-2",
                com.routechain.core.CompactPlanType.BATCH_2_COMPACT,
                List.of("order-1", "order-2"),
                features,
                breakdown,
                snapshotBefore,
                Instant.parse("2026-04-12T04:30:00Z"),
                16.0,
                1.2,
                84000.0,
                0.78,
                0.71,
                0.7,
                2.0,
                0.06,
                0.82,
                5.6);

        ledger.recordAccepted("trace-bundle", Instant.parse("2026-04-12T04:31:00Z"));
        ledger.recordOrderDelivered("trace-bundle", "order-1", true, 42000.0, 15.0, Instant.parse("2026-04-12T04:40:00Z"));
        ledger.recordOrderDelivered("trace-bundle", "order-2", false, 42000.0, 18.0, Instant.parse("2026-04-12T04:45:00Z"));
        ledger.markDriverIdle("driver-bundle", 50L, Instant.parse("2026-04-12T04:45:00Z"), new com.routechain.domain.GeoPoint(10.776, 106.701));

        CompactDecisionResolution resolution = ledger.recordPostDropHit(
                "driver-bundle",
                54L,
                Instant.parse("2026-04-12T04:46:00Z"),
                new com.routechain.domain.GeoPoint(10.781, 106.707));

        assertNotNull(resolution);
        assertEquals(2, resolution.orderIds().size());
        assertEquals(com.routechain.core.CompactPlanType.BATCH_2_COMPACT, resolution.decisionLog().planType());
        assertEquals(16.5, resolution.resolvedSample().actualEtaMinutes(), 1e-9);
        assertEquals(1.0, resolution.outcomeVector().completion(), 1e-9);
        assertEquals(0.5, resolution.outcomeVector().onTime(), 1e-9);
        assertTrue(resolution.postDropHit());
    }
}
