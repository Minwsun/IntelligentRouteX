package com.routechain.data.memory;

import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferRecord;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.backend.offer.OfferReservation;
import com.routechain.data.port.OfferStateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fallback in-memory offer state store for local development.
 */
@Component
@ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryOfferStateStore implements OfferStateStore {
    private final Map<String, DriverOfferBatch> batchesById = new ConcurrentHashMap<>();
    private final Map<String, DriverOfferRecord> offersById = new ConcurrentHashMap<>();
    private final Map<String, List<String>> offersByDriver = new ConcurrentHashMap<>();
    private final Map<String, OfferReservation> reservationsByOrder = new ConcurrentHashMap<>();
    private final List<OfferDecision> decisions = new CopyOnWriteArrayList<>();

    @Override
    public void saveBatch(DriverOfferBatch batch) {
        batchesById.put(batch.offerBatchId(), batch);
    }

    @Override
    public Optional<DriverOfferBatch> findBatch(String batchId) {
        return Optional.ofNullable(batchesById.get(batchId));
    }

    @Override
    public Optional<DriverOfferBatch> latestBatchForOrder(String orderId) {
        return batchesById.values().stream()
                .filter(batch -> batch.orderId().equals(orderId))
                .max(Comparator.comparing(DriverOfferBatch::createdAt));
    }

    @Override
    public List<DriverOfferBatch> batchesForOrder(String orderId) {
        return batchesById.values().stream()
                .filter(batch -> batch.orderId().equals(orderId))
                .sorted(Comparator.comparing(DriverOfferBatch::createdAt))
                .toList();
    }

    @Override
    public void saveOffer(DriverOfferRecord offer) {
        offersById.put(offer.offerId(), offer);
        offersByDriver.computeIfAbsent(offer.driverId(), ignored -> new CopyOnWriteArrayList<>());
        if (!offersByDriver.get(offer.driverId()).contains(offer.offerId())) {
            offersByDriver.get(offer.driverId()).add(offer.offerId());
        }
    }

    @Override
    public Optional<DriverOfferRecord> findOffer(String offerId) {
        return Optional.ofNullable(offersById.get(offerId));
    }

    @Override
    public List<DriverOfferRecord> offersForDriver(String driverId) {
        List<String> offerIds = offersByDriver.getOrDefault(driverId, List.of());
        List<DriverOfferRecord> offers = new ArrayList<>();
        for (String offerId : offerIds) {
            DriverOfferRecord offer = offersById.get(offerId);
            if (offer != null) {
                offers.add(offer);
            }
        }
        offers.sort(Comparator.comparing(DriverOfferRecord::createdAt).reversed());
        return List.copyOf(offers);
    }

    @Override
    public List<DriverOfferRecord> offersForBatch(String batchId) {
        return offersById.values().stream()
                .filter(offer -> offer.offerBatchId().equals(batchId))
                .sorted(Comparator.comparing(DriverOfferRecord::createdAt))
                .toList();
    }

    @Override
    public List<DriverOfferRecord> offersForOrder(String orderId) {
        return offersById.values().stream()
                .filter(offer -> offer.orderId().equals(orderId))
                .sorted(Comparator.comparing(DriverOfferRecord::createdAt))
                .toList();
    }

    @Override
    public List<DriverOfferRecord> allOffers() {
        return offersById.values().stream()
                .sorted(Comparator.comparing(DriverOfferRecord::createdAt))
                .toList();
    }

    @Override
    public void saveReservation(OfferReservation reservation) {
        reservationsByOrder.put(reservation.orderId(), reservation);
    }

    @Override
    public Optional<OfferReservation> findReservation(String orderId) {
        return Optional.ofNullable(reservationsByOrder.get(orderId));
    }

    @Override
    public List<OfferReservation> allReservations() {
        return reservationsByOrder.values().stream().toList();
    }

    @Override
    public void saveDecision(OfferDecision decision) {
        decisions.add(decision);
    }

    @Override
    public List<OfferDecision> decisionsForOrder(String orderId) {
        return decisions.stream()
                .filter(decision -> orderId.equals(decision.orderId()))
                .sorted(Comparator.comparing(OfferDecision::decidedAt))
                .toList();
    }
}
