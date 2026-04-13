package com.routechain.api.service;

import com.routechain.api.dto.AssignmentLockView;
import com.routechain.api.dto.OfferWaveSummary;
import com.routechain.api.dto.OrderOfferSnapshot;
import com.routechain.api.dto.OrderOfferStage;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferRecord;
import com.routechain.backend.offer.DriverOfferStatus;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.backend.offer.OfferReservation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class OrderOfferViewMapper {
    private OrderOfferViewMapper() {
    }

    public static OrderOfferSnapshot emptySnapshot() {
        return new OrderOfferSnapshot(
                OrderOfferStage.NONE,
                "",
                0,
                0,
                false,
                false,
                "",
                null,
                null
        );
    }

    public static OrderOfferSnapshot snapshot(List<DriverOfferBatch> batches,
                                              List<DriverOfferRecord> offers,
                                              List<OfferDecision> decisions,
                                              OfferReservation reservation,
                                              String assignedDriverId) {
        List<DriverOfferBatch> safeBatches = batches == null ? List.of() : batches.stream()
                .sorted(Comparator.comparing(DriverOfferBatch::createdAt))
                .toList();
        List<DriverOfferRecord> safeOffers = offers == null ? List.of() : offers;
        List<OfferDecision> safeDecisions = decisions == null ? List.of() : decisions;
        DriverOfferBatch latestBatch = safeBatches.isEmpty() ? null : safeBatches.getLast();
        boolean pendingOffersPresent = latestBatch != null && safeOffers.stream()
                .anyMatch(offer -> offer.offerBatchId().equals(latestBatch.offerBatchId()) && offer.status() == DriverOfferStatus.PENDING);
        OfferDecision latestDecision = safeDecisions.isEmpty() ? null : safeDecisions.getLast();
        OrderOfferStage stage = stageFor(latestBatch, latestDecision, reservation, assignedDriverId, pendingOffersPresent);
        AssignmentLockView assignmentLock = reservation == null ? null : new AssignmentLockView(
                reservation.reservationId(),
                reservation.driverId(),
                reservation.acceptedOfferId(),
                reservation.reservationVersion(),
                reservation.status(),
                toIso(reservation.reservedAt()),
                toIso(reservation.expiresAt()));
        OfferWaveSummary latestWave = latestBatch == null ? null : new OfferWaveSummary(
                latestBatch.offerBatchId(),
                latestBatch.wave(),
                latestBatch.previousBatchId(),
                waveStage(latestBatch, pendingOffersPresent),
                latestBatch.fanout(),
                countOffers(safeOffers, latestBatch.offerBatchId(), DriverOfferStatus.PENDING),
                countResolvedOffers(safeOffers, latestBatch.offerBatchId()),
                toIso(latestBatch.createdAt()),
                toIso(latestBatch.expiresAt()),
                toIso(latestBatch.closedAt()),
                latestBatch.closeReason());
        boolean reofferEligible = latestBatch != null
                && latestBatch.isClosed()
                && !hasAcceptedReservation(reservation)
                && !"accepted".equalsIgnoreCase(latestBatch.closeReason())
                && !"locked_assignment".equalsIgnoreCase(latestBatch.closeReason());
        return new OrderOfferSnapshot(
                stage,
                latestBatch == null ? "" : latestBatch.offerBatchId(),
                latestBatch == null ? 0 : latestBatch.wave(),
                safeBatches.size(),
                reofferEligible,
                pendingOffersPresent,
                latestDecision == null ? "" : latestDecision.reason(),
                latestWave,
                assignmentLock
        );
    }

    public static OrderOfferStage driverOfferStage(DriverOfferRecord offer,
                                                   DriverOfferBatch batch,
                                                   OfferReservation reservation,
                                                   String assignedDriverId) {
        if (offer == null) {
            return OrderOfferStage.NONE;
        }
        return switch (offer.status()) {
            case PENDING -> batch != null && batch.wave() > 1 ? OrderOfferStage.REOFFERED : OrderOfferStage.OFFERED;
            case DECLINED -> OrderOfferStage.DECLINED;
            case EXPIRED -> OrderOfferStage.EXPIRED;
            case LOST -> OrderOfferStage.LOST;
            case ACCEPTED -> hasAcceptedReservation(reservation)
                    && reservation.driverId().equals(offer.driverId())
                    && assignedDriverId != null
                    && assignedDriverId.equals(offer.driverId())
                    ? OrderOfferStage.LOCKED_ASSIGNMENT
                    : OrderOfferStage.ACCEPTED;
        };
    }

    private static OrderOfferStage stageFor(DriverOfferBatch latestBatch,
                                            OfferDecision latestDecision,
                                            OfferReservation reservation,
                                            String assignedDriverId,
                                            boolean pendingOffersPresent) {
        if (hasAcceptedReservation(reservation)) {
            return assignedDriverId != null && !assignedDriverId.isBlank()
                    ? OrderOfferStage.LOCKED_ASSIGNMENT
                    : OrderOfferStage.ACCEPTED;
        }
        if (latestBatch == null) {
            return OrderOfferStage.NONE;
        }
        if (pendingOffersPresent) {
            return latestBatch.wave() > 1 ? OrderOfferStage.REOFFERED : OrderOfferStage.OFFERED;
        }
        if (!latestBatch.isClosed()) {
            return OrderOfferStage.BATCH_CREATED;
        }
        if (latestDecision == null) {
            return OrderOfferStage.CLOSED;
        }
        return switch (latestDecision.status()) {
            case PENDING -> OrderOfferStage.BATCH_CREATED;
            case DECLINED -> OrderOfferStage.DECLINED;
            case EXPIRED -> OrderOfferStage.EXPIRED;
            case LOST -> OrderOfferStage.LOST;
            case ACCEPTED -> OrderOfferStage.ACCEPTED;
        };
    }

    private static String waveStage(DriverOfferBatch batch, boolean pendingOffersPresent) {
        if (batch == null) {
            return "NONE";
        }
        if (pendingOffersPresent) {
            return batch.wave() > 1 ? "REOFFERED" : "OFFERED";
        }
        if (batch.isClosed()) {
            return "CLOSED";
        }
        return "BATCH_CREATED";
    }

    private static boolean hasAcceptedReservation(OfferReservation reservation) {
        return reservation != null && "ACCEPTED".equalsIgnoreCase(reservation.status());
    }

    private static int countOffers(List<DriverOfferRecord> offers, String batchId, DriverOfferStatus status) {
        return (int) offers.stream()
                .filter(offer -> batchId.equals(offer.offerBatchId()) && offer.status() == status)
                .count();
    }

    private static int countResolvedOffers(List<DriverOfferRecord> offers, String batchId) {
        return (int) offers.stream()
                .filter(offer -> batchId.equals(offer.offerBatchId()) && offer.status() != DriverOfferStatus.PENDING)
                .count();
    }

    private static String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
