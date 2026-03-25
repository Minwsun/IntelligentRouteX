package com.routechain.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqQuotaTrackerTest {

    @Test
    void shouldRejectRpmExhaustionUntilWindowExpires() {
        GroqQuotaTracker tracker = new GroqQuotaTracker();
        GroqModelCatalog.ModelSpec model = new GroqModelCatalog.ModelSpec("test-model", 1, 100, 10_000, 100_000);
        Instant now = Instant.parse("2026-03-25T10:00:00Z");

        GroqQuotaTracker.Reservation first = tracker.tryReserve(model, 120, now);
        GroqQuotaTracker.Reservation second = tracker.tryReserve(model, 120, now.plusSeconds(10));
        GroqQuotaTracker.Reservation third = tracker.tryReserve(model, 120, now.plusSeconds(61));

        assertTrue(first.accepted());
        assertFalse(second.accepted());
        assertEquals("rpm-exhausted", second.rejectionReason());
        assertTrue(third.accepted());
    }

    @Test
    void shouldTemporarilyBlockModelWhenQuotaExceededIsReported() {
        GroqQuotaTracker tracker = new GroqQuotaTracker();
        GroqModelCatalog.ModelSpec model = new GroqModelCatalog.ModelSpec("test-model", 10, 100, 10_000, 100_000);
        Instant now = Instant.parse("2026-03-25T10:00:00Z");

        tracker.markQuotaExceeded(model.modelId(), now);
        GroqQuotaTracker.Reservation blocked = tracker.tryReserve(model, 90, now.plusSeconds(30));
        GroqQuotaTracker.Reservation recovered = tracker.tryReserve(model, 90, now.plusSeconds(61));

        assertFalse(blocked.accepted());
        assertEquals("quota-blocked", blocked.rejectionReason());
        assertTrue(recovered.accepted());
    }
}
