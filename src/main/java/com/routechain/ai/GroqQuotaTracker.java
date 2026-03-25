package com.routechain.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local quota guard that prevents avoidable free-tier rate-limit hits.
 */
public final class GroqQuotaTracker {

    public record Reservation(
            boolean accepted,
            String modelId,
            String leaseId,
            int reservedTokens,
            String rejectionReason
    ) {
        public static Reservation rejected(String modelId, String rejectionReason) {
            return new Reservation(false, modelId, "", 0, rejectionReason);
        }
    }

    private static final class ReservationEntry {
        private final String leaseId;
        private final Instant createdAt;
        private int tokens;

        private ReservationEntry(String leaseId, Instant createdAt, int tokens) {
            this.leaseId = leaseId;
            this.createdAt = createdAt;
            this.tokens = tokens;
        }
    }

    private static final class ModelWindow {
        private final ArrayDeque<ReservationEntry> minuteReservations = new ArrayDeque<>();
        private final Map<String, ReservationEntry> reservationByLeaseId = new ConcurrentHashMap<>();
        private LocalDate activeDay = LocalDate.now(ZoneOffset.UTC);
        private int requestsToday = 0;
        private int tokensToday = 0;
        private Instant quotaBlockedUntil = Instant.EPOCH;
    }

    private final Map<String, ModelWindow> windows = new ConcurrentHashMap<>();

    public synchronized Reservation tryReserve(GroqModelCatalog.ModelSpec model,
                                               int estimatedTotalTokens,
                                               Instant now) {
        ModelWindow window = windows.computeIfAbsent(model.modelId(), ignored -> new ModelWindow());
        prune(window, now);
        rolloverDay(window, now);
        if (now.isBefore(window.quotaBlockedUntil)) {
            return Reservation.rejected(model.modelId(), "quota-blocked");
        }

        int minuteRequests = window.minuteReservations.size();
        int minuteTokens = window.minuteReservations.stream().mapToInt(entry -> entry.tokens).sum();
        if (minuteRequests + 1 > model.rpm()) {
            return Reservation.rejected(model.modelId(), "rpm-exhausted");
        }
        if (minuteTokens + estimatedTotalTokens > model.tpm()) {
            return Reservation.rejected(model.modelId(), "tpm-exhausted");
        }
        if (window.requestsToday + 1 > model.rpd()) {
            return Reservation.rejected(model.modelId(), "rpd-exhausted");
        }
        if (window.tokensToday + estimatedTotalTokens > model.tpd()) {
            return Reservation.rejected(model.modelId(), "tpd-exhausted");
        }

        String leaseId = UUID.randomUUID().toString();
        ReservationEntry entry = new ReservationEntry(leaseId, now, estimatedTotalTokens);
        window.minuteReservations.addLast(entry);
        window.reservationByLeaseId.put(leaseId, entry);
        window.requestsToday++;
        window.tokensToday += estimatedTotalTokens;
        return new Reservation(true, model.modelId(), leaseId, estimatedTotalTokens, "accepted");
    }

    public synchronized void settle(Reservation reservation, int actualTotalTokens) {
        if (reservation == null || !reservation.accepted()) {
            return;
        }
        ModelWindow window = windows.get(reservation.modelId());
        if (window == null) {
            return;
        }
        ReservationEntry entry = window.reservationByLeaseId.get(reservation.leaseId());
        if (entry == null) {
            return;
        }
        int sanitizedActual = Math.max(1, actualTotalTokens);
        int delta = sanitizedActual - entry.tokens;
        entry.tokens = sanitizedActual;
        window.tokensToday = Math.max(0, window.tokensToday + delta);
    }

    public synchronized void markQuotaExceeded(String modelId, Instant now) {
        ModelWindow window = windows.computeIfAbsent(modelId, ignored -> new ModelWindow());
        window.quotaBlockedUntil = now.plusSeconds(60);
    }

    private void prune(ModelWindow window, Instant now) {
        while (!window.minuteReservations.isEmpty()) {
            ReservationEntry head = window.minuteReservations.peekFirst();
            if (head == null || head.createdAt.plusSeconds(60).isAfter(now)) {
                break;
            }
            window.minuteReservations.removeFirst();
            window.reservationByLeaseId.remove(head.leaseId);
        }
    }

    private void rolloverDay(ModelWindow window, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!today.equals(window.activeDay)) {
            window.activeDay = today;
            window.requestsToday = 0;
            window.tokensToday = 0;
        }
    }
}
