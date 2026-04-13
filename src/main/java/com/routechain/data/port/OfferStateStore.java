package com.routechain.data.port;

import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferRecord;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.backend.offer.OfferReservation;

import java.util.List;
import java.util.Optional;

public interface OfferStateStore {
    void saveBatch(DriverOfferBatch batch);
    Optional<DriverOfferBatch> findBatch(String batchId);
    Optional<DriverOfferBatch> latestBatchForOrder(String orderId);
    List<DriverOfferBatch> batchesForOrder(String orderId);

    void saveOffer(DriverOfferRecord offer);
    Optional<DriverOfferRecord> findOffer(String offerId);
    List<DriverOfferRecord> offersForDriver(String driverId);
    List<DriverOfferRecord> offersForBatch(String batchId);
    List<DriverOfferRecord> offersForOrder(String orderId);
    List<DriverOfferRecord> allOffers();

    void saveReservation(OfferReservation reservation);
    Optional<OfferReservation> findReservation(String orderId);
    List<OfferReservation> allReservations();

    void saveDecision(OfferDecision decision);
    List<OfferDecision> decisionsForOrder(String orderId);
}
