package com.routechain.backend.offer;

import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.api.service.OrderOfferViewMapper;
import com.routechain.data.memory.InMemoryOfferRuntimeStore;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.model.OrderLifecycleFactType;
import com.routechain.data.port.OfferRuntimeStore;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OrderLifecycleFactService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.infra.Events;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Screened offer fanout broker for driver app flows.
 */
public class OfferBrokerService {
    private static final int MAX_FANOUT = 3;
    private static final Duration DEFAULT_DECLINE_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration DEFAULT_EXPIRY_COOLDOWN = Duration.ofSeconds(45);

    private final OfferStateStore stateStore;
    private final OfferRuntimeStore runtimeStore;
    private final OperationalEventPublisher eventPublisher;
    private final OrderLifecycleFactService lifecycleFactService;
    private final Duration declineCooldown;
    private final Duration expiryCooldown;

    public OfferBrokerService() {
        this(new InMemoryOperationalStore());
    }

    private OfferBrokerService(InMemoryOperationalStore operationalStore) {
        this(
                new InMemoryOfferStateStore(),
                new InMemoryOfferRuntimeStore(),
                new OrderLifecycleFactService(operationalStore),
                new OperationalEventPublisher(operationalStore),
                DEFAULT_DECLINE_COOLDOWN,
                DEFAULT_EXPIRY_COOLDOWN
        );
    }

    public OfferBrokerService(OfferStateStore stateStore,
                              OrderLifecycleFactService lifecycleFactService,
                              OperationalEventPublisher eventPublisher) {
        this(
                stateStore,
                new InMemoryOfferRuntimeStore(),
                lifecycleFactService,
                eventPublisher,
                DEFAULT_DECLINE_COOLDOWN,
                DEFAULT_EXPIRY_COOLDOWN
        );
    }

    public OfferBrokerService(OfferStateStore stateStore,
                              OfferRuntimeStore runtimeStore,
                              OrderLifecycleFactService lifecycleFactService,
                              OperationalEventPublisher eventPublisher,
                              Duration declineCooldown,
                              Duration expiryCooldown) {
        this.stateStore = stateStore;
        this.runtimeStore = runtimeStore;
        this.lifecycleFactService = lifecycleFactService;
        this.eventPublisher = eventPublisher;
        this.declineCooldown = normalizeCooldown(declineCooldown, DEFAULT_DECLINE_COOLDOWN);
        this.expiryCooldown = normalizeCooldown(expiryCooldown, DEFAULT_EXPIRY_COOLDOWN);
    }

    public synchronized DriverOfferBatch publishOffers(String orderId,
                                                       String serviceTier,
                                                       List<DriverOfferCandidate> screenedCandidates,
                                                       int requestedFanout) {
        return publishOffers(orderId, serviceTier, screenedCandidates, requestedFanout, 1, "");
    }

    public synchronized DriverOfferBatch publishOffers(String orderId,
                                                       String serviceTier,
                                                       List<DriverOfferCandidate> screenedCandidates,
                                                       int requestedFanout,
                                                       int wave,
                                                       String previousBatchId) {
        int requestedLimit = Math.max(1, Math.min(MAX_FANOUT, requestedFanout));
        List<DriverOfferCandidate> ranked = screenedCandidates == null
                ? List.of()
                : screenedCandidates.stream()
                .filter(candidate -> runtimeStore.driverCooldownUntil(candidate.driverId()).isEmpty())
                .sorted(Comparator
                        .comparingDouble(DriverOfferCandidate::score).reversed()
                        .thenComparing(Comparator.comparingDouble(DriverOfferCandidate::acceptanceProbability).reversed())
                        .thenComparingDouble(DriverOfferCandidate::deadheadKm))
                .limit(requestedLimit)
                .toList();
        String batchId = "offer-batch-" + UUID.randomUUID().toString().substring(0, 8);
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(defaultTtl(serviceTier));
        List<String> offerIds = new ArrayList<>();
        for (DriverOfferCandidate candidate : ranked) {
            String offerId = "offer-" + UUID.randomUUID().toString().substring(0, 8);
            offerIds.add(offerId);
            stateStore.saveOffer(new DriverOfferRecord(
                    offerId,
                    batchId,
                    candidate.orderId(),
                    candidate.driverId(),
                    candidate.serviceTier(),
                    candidate.score(),
                    candidate.acceptanceProbability(),
                    candidate.deadheadKm(),
                    candidate.borrowed(),
                    candidate.rationale(),
                    DriverOfferStatus.PENDING,
                    createdAt,
                    expiresAt
            ));
            runtimeStore.markOfferActive(offerId, expiresAt);
            eventPublisher.publish(
                    "offer.created.v1",
                    "ORDER",
                    candidate.orderId(),
                    new Events.DriverOfferCreated(
                    offerId,
                    batchId,
                    candidate.orderId(),
                    candidate.driverId(),
                    candidate.score(),
                    candidate.acceptanceProbability(),
                    expiresAt
            ));
        }
        DriverOfferBatch batch = new DriverOfferBatch(
                batchId,
                orderId,
                serviceTier,
                ranked.size(),
                createdAt,
                expiresAt,
                Math.max(1, wave),
                previousBatchId == null ? "" : previousBatchId,
                null,
                "",
                offerIds,
                ranked
        );
        stateStore.saveBatch(batch);
        lifecycleFactService.append(
                batch.orderId(),
                OrderLifecycleFactType.OFFER_BATCH_CREATED,
                "SYSTEM",
                "offer-broker-service",
                createdAt,
                Map.of(
                        "offerBatchId", batch.offerBatchId(),
                        "wave", batch.wave(),
                        "previousBatchId", batch.previousBatchId(),
                        "fanout", batch.fanout(),
                        "expiresAt", batch.expiresAt().toString())
        );
        if (batch.wave() > 1) {
            lifecycleFactService.append(
                    batch.orderId(),
                    OrderLifecycleFactType.ORDER_REOFFERED,
                    "SYSTEM",
                    "offer-broker-service",
                    createdAt,
                    Map.of(
                            "previousBatchId", batch.previousBatchId(),
                            "nextBatchId", batch.offerBatchId(),
                            "wave", batch.wave(),
                            "reason", "reoffer_wave_created")
            );
        }
        eventPublisher.publish(
                "offer.created.v1",
                "ORDER",
                batch.orderId(),
                new Events.OfferBatchCreated(
                batch.offerBatchId(),
                batch.orderId(),
                batch.serviceTier(),
                batch.fanout(),
                batch.wave(),
                batch.previousBatchId(),
                batch.createdAt(),
                batch.expiresAt()
        ));
        return batch;
    }

    public synchronized List<OfferView> offersForDriver(String driverId) {
        expireStaleOffers();
        List<OfferView> views = new ArrayList<>();
        for (DriverOfferRecord entry : stateStore.offersForDriver(driverId)) {
            if (entry.status() == DriverOfferStatus.PENDING
                    || entry.status() == DriverOfferStatus.ACCEPTED
                    || entry.status() == DriverOfferStatus.DECLINED
                    || entry.status() == DriverOfferStatus.EXPIRED
                    || entry.status() == DriverOfferStatus.LOST) {
                DriverOfferBatch batch = stateStore.findBatch(entry.offerBatchId()).orElse(null);
                OfferReservation reservation = stateStore.findReservation(entry.orderId()).orElse(null);
                long reservationVersion = reservation == null ? 0L : reservation.reservationVersion();
                views.add(new OfferView(
                        entry.offerId(),
                        entry.offerBatchId(),
                        entry.orderId(),
                        entry.driverId(),
                        entry.serviceTier(),
                        entry.score(),
                        entry.acceptanceProbability(),
                        entry.deadheadKm(),
                        entry.borrowed(),
                        entry.rationale(),
                        entry.status(),
                        OrderOfferViewMapper.driverOfferStage(entry, batch, reservation, reservation == null ? "" : reservation.driverId()),
                        batch == null ? 1 : batch.wave(),
                        batch == null ? "" : batch.previousBatchId(),
                        reservationVersion,
                        entry.createdAt(),
                        entry.expiresAt()
                ));
            }
        }
        views.sort(Comparator.comparing(OfferView::createdAt).reversed());
        return List.copyOf(views);
    }

    public synchronized OfferDecision acceptOffer(String offerId, String driverId) {
        expireStaleOffers();
        DriverOfferRecord entry = stateStore.findOffer(offerId).orElse(null);
        Instant now = Instant.now();
        if (entry == null) {
            return new OfferDecision(offerId, "", "order-unknown", driverId, DriverOfferStatus.LOST, "offer-not-found", now, 0);
        }
        if (!entry.driverId().equals(driverId)) {
            return new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, DriverOfferStatus.LOST, "driver-mismatch", now, 0);
        }
        if (entry.status() != DriverOfferStatus.PENDING) {
            String terminalReason = switch (entry.status()) {
                case LOST -> "offer-lost";
                case EXPIRED -> "offer-expired";
                case DECLINED -> "offer-declined";
                case ACCEPTED -> "offer-already-accepted";
                default -> "offer-not-pending";
            };
            return new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, entry.status(), terminalReason, now,
                    stateStore.findReservation(entry.orderId())
                            .orElse(OfferReservationDefaults.empty(entry.orderId()))
                            .reservationVersion());
        }
        if (!runtimeStore.isOfferActive(offerId, now)) {
            expireOffer(entry, now);
            return new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, DriverOfferStatus.EXPIRED, "offer-expired", now, 0);
        }
        OfferReservation existing = stateStore.findReservation(entry.orderId()).orElse(null);
        if (existing != null && "ACCEPTED".equalsIgnoreCase(existing.status())) {
            markLost(entry.offerBatchId(), offerId, "offer-lost");
            OfferDecision decision = new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, DriverOfferStatus.LOST, "offer-lost", now, existing.reservationVersion());
            stateStore.saveDecision(decision);
            appendOfferFact(OrderLifecycleFactType.OFFER_LOST, entry, now, Map.of(
                    "offerId", entry.offerId(),
                    "offerBatchId", entry.offerBatchId(),
                    "driverId", driverId,
                    "reason", "offer-lost"));
            closeBatchIfResolved(entry.offerBatchId(), now, "accepted_elsewhere");
            return decision;
        }

        long reservationVersion = existing == null ? 1L : existing.reservationVersion() + 1L;
        OfferReservation reservation = new OfferReservation(
                "reservation-" + UUID.randomUUID().toString().substring(0, 8),
                entry.orderId(),
                entry.offerBatchId(),
                entry.offerId(),
                reservationVersion,
                driverId,
                now,
                entry.expiresAt(),
                "ACCEPTED"
        );
        stateStore.saveReservation(reservation);
        stateStore.saveOffer(entry.withStatus(DriverOfferStatus.ACCEPTED));
        runtimeStore.clearOffer(offerId);
        markLost(entry.offerBatchId(), offerId, "offer-lost");
        appendOfferFact(OrderLifecycleFactType.OFFER_ACCEPTED, entry, now, Map.of(
                "offerId", entry.offerId(),
                "offerBatchId", entry.offerBatchId(),
                "driverId", driverId,
                "reservationVersion", reservationVersion,
                "expiresAt", entry.expiresAt().toString(),
                "reason", "accepted"));
        lifecycleFactService.append(
                entry.orderId(),
                OrderLifecycleFactType.ASSIGNMENT_LOCKED,
                "SYSTEM",
                "offer-broker-service",
                now,
                Map.of(
                        "reservationId", reservation.reservationId(),
                        "offerId", entry.offerId(),
                        "offerBatchId", entry.offerBatchId(),
                        "driverId", driverId,
                        "reservationVersion", reservationVersion,
                        "status", reservation.status(),
                        "expiresAt", reservation.expiresAt().toString())
        );
        eventPublisher.publish(
                "offer.resolved.v1",
                "ORDER",
                entry.orderId(),
                new Events.DriverOfferAccepted(offerId, entry.offerBatchId(), entry.orderId(), driverId, now));
        eventPublisher.publish(
                "assignment.locked.v1",
                "ORDER",
                entry.orderId(),
                new Events.AssignmentLocked(entry.orderId(), offerId, entry.offerBatchId(), driverId, reservationVersion, now));
        OfferDecision decision = new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, DriverOfferStatus.ACCEPTED, "accepted", now, reservationVersion);
        stateStore.saveDecision(decision);
        closeBatchIfResolved(entry.offerBatchId(), now, "accepted");
        return decision;
    }

    public synchronized OfferDecision declineOffer(String offerId, String driverId, String reason) {
        expireStaleOffers();
        DriverOfferRecord entry = stateStore.findOffer(offerId).orElse(null);
        Instant now = Instant.now();
        if (entry == null) {
            return new OfferDecision(offerId, "", "order-unknown", driverId, DriverOfferStatus.LOST, "offer-not-found", now, 0);
        }
        if (!entry.driverId().equals(driverId)) {
            return new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, DriverOfferStatus.LOST, "driver-mismatch", now, 0);
        }
        if (entry.status() != DriverOfferStatus.PENDING) {
            return new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, entry.status(), "offer-not-pending", now, 0);
        }
        stateStore.saveOffer(entry.withStatus(DriverOfferStatus.DECLINED));
        runtimeStore.clearOffer(offerId);
        runtimeStore.markDriverCooldown(driverId, now.plus(declineCooldown));
        String resolvedReason = reason == null || reason.isBlank() ? "declined" : reason;
        eventPublisher.publish(
                "offer.resolved.v1",
                "ORDER",
                entry.orderId(),
                new Events.DriverOfferDeclined(offerId, entry.offerBatchId(), entry.orderId(), driverId,
                        resolvedReason, now));
        OfferDecision decision = new OfferDecision(offerId, entry.offerBatchId(), entry.orderId(), driverId, DriverOfferStatus.DECLINED,
                resolvedReason, now, 0);
        stateStore.saveDecision(decision);
        appendOfferFact(OrderLifecycleFactType.OFFER_DECLINED, entry, now, Map.of(
                "offerId", entry.offerId(),
                "offerBatchId", entry.offerBatchId(),
                "driverId", driverId,
                "reason", resolvedReason));
        closeBatchIfResolved(entry.offerBatchId(), now, "declined");
        return decision;
    }

    public synchronized Map<String, OfferReservation> activeReservations() {
        expireStaleOffers();
        Map<String, OfferReservation> reservations = new LinkedHashMap<>();
        for (OfferReservation reservation : stateStore.allReservations()) {
            reservations.put(reservation.orderId(), reservation);
        }
        return Map.copyOf(reservations);
    }

    private void markLost(String batchId, String acceptedOfferId, String reason) {
        DriverOfferBatch batch = stateStore.findBatch(batchId).orElse(null);
        if (batch == null) {
            return;
        }
        for (DriverOfferRecord pending : stateStore.offersForBatch(batch.offerBatchId())) {
            if (pending.offerId().equals(acceptedOfferId)) {
                continue;
            }
            if (pending.status() == DriverOfferStatus.PENDING) {
                stateStore.saveOffer(pending.withStatus(DriverOfferStatus.LOST));
                runtimeStore.clearOffer(pending.offerId());
                eventPublisher.publish(
                        "offer.resolved.v1",
                        "ORDER",
                        pending.orderId(),
                        new Events.DriverOfferLost(pending.offerId(), pending.offerBatchId(), pending.orderId(),
                                pending.driverId(), reason, Instant.now()));
                stateStore.saveDecision(new OfferDecision(
                        pending.offerId(),
                        pending.offerBatchId(),
                        pending.orderId(),
                        pending.driverId(),
                        DriverOfferStatus.LOST,
                        reason,
                        Instant.now(),
                        0
                ));
                appendOfferFact(OrderLifecycleFactType.OFFER_LOST, pending, Instant.now(), Map.of(
                        "offerId", pending.offerId(),
                        "offerBatchId", pending.offerBatchId(),
                        "driverId", pending.driverId(),
                        "reason", reason));
            }
        }
    }

    private void expireStaleOffers() {
        Instant now = Instant.now();
        for (DriverOfferRecord entry : stateStore.allOffers()) {
            if (entry.status() == DriverOfferStatus.PENDING && entry.expiresAt().isBefore(now)) {
                expireOffer(entry, now);
            }
        }
    }

    private Duration defaultTtl(String serviceTier) {
        String normalized = serviceTier == null ? "instant" : serviceTier.trim().toLowerCase();
        return switch (normalized) {
            case "2h" -> Duration.ofSeconds(18);
            case "4h", "scheduled" -> Duration.ofSeconds(24);
            default -> Duration.ofSeconds(10);
        };
    }

    private void expireOffer(DriverOfferRecord entry, Instant now) {
        if (entry == null || entry.status() != DriverOfferStatus.PENDING) {
            return;
        }
        stateStore.saveOffer(entry.withStatus(DriverOfferStatus.EXPIRED));
        runtimeStore.clearOffer(entry.offerId());
        runtimeStore.markDriverCooldown(entry.driverId(), now.plus(expiryCooldown));
        eventPublisher.publish(
                "offer.resolved.v1",
                "ORDER",
                entry.orderId(),
                new Events.DriverOfferExpired(entry.offerId(), entry.offerBatchId(), entry.orderId(), entry.driverId(), now));
        stateStore.saveDecision(new OfferDecision(
                entry.offerId(),
                entry.offerBatchId(),
                entry.orderId(),
                entry.driverId(),
                DriverOfferStatus.EXPIRED,
                "offer-expired",
                now,
                0
        ));
        appendOfferFact(OrderLifecycleFactType.OFFER_EXPIRED, entry, now, Map.of(
                "offerId", entry.offerId(),
                "offerBatchId", entry.offerBatchId(),
                "driverId", entry.driverId(),
                "reason", "offer-expired"));
        closeBatchIfResolved(entry.offerBatchId(), now, "expired");
    }

    private void closeBatchIfResolved(String batchId, Instant now, String reason) {
        DriverOfferBatch batch = stateStore.findBatch(batchId).orElse(null);
        if (batch == null || batch.isClosed()) {
            return;
        }
        boolean hasPending = stateStore.offersForBatch(batchId).stream()
                .anyMatch(offer -> offer.status() == DriverOfferStatus.PENDING);
        if (hasPending) {
            return;
        }
        DriverOfferBatch closed = batch.close(now, reason);
        stateStore.saveBatch(closed);
        lifecycleFactService.append(
                closed.orderId(),
                OrderLifecycleFactType.OFFER_BATCH_CLOSED,
                "SYSTEM",
                "offer-broker-service",
                now,
                Map.of(
                        "offerBatchId", closed.offerBatchId(),
                        "wave", closed.wave(),
                        "closeReason", closed.closeReason())
        );
        eventPublisher.publish(
                "offer.batch_closed.v1",
                "ORDER",
                closed.orderId(),
                new Events.OfferBatchClosed(closed.offerBatchId(), closed.orderId(), closed.wave(), closed.closeReason(), closed.closedAt()));
    }

    private void appendOfferFact(OrderLifecycleFactType factType,
                                 DriverOfferRecord offer,
                                 Instant recordedAt,
                                 Map<String, Object> payload) {
        lifecycleFactService.append(
                offer.orderId(),
                factType,
                "DRIVER",
                offer.driverId(),
                recordedAt,
                payload
        );
    }

    private Duration normalizeCooldown(Duration value, Duration fallback) {
        if (value == null || value.isNegative() || value.isZero()) {
            return fallback;
        }
        return value;
    }

    public record OfferView(
            String offerId,
            String offerBatchId,
            String orderId,
            String driverId,
            String serviceTier,
            double score,
            double acceptanceProbability,
            double deadheadKm,
            boolean borrowed,
            String rationale,
            DriverOfferStatus status,
            com.routechain.api.dto.OrderOfferStage offerStage,
            int wave,
            String previousBatchId,
            long reservationVersion,
            Instant createdAt,
            Instant expiresAt
    ) {}

    private static final class OfferReservationDefaults {
        private static OfferReservation empty(String orderId) {
            return new OfferReservation("reservation-empty", orderId, "offer-batch-empty", "", 0L,
                    "driver-unset", Instant.EPOCH, Instant.EPOCH, "NONE");
        }
    }
}
