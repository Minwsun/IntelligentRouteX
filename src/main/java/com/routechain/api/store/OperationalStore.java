package com.routechain.api.store;

import com.routechain.backend.offer.DriverSessionState;
import com.routechain.domain.Order;

import java.util.Collection;
import java.util.Optional;

/**
 * Storage boundary for mobile-facing operational state.
 * Keeps API/business services decoupled from the concrete persistence backend.
 */
public interface OperationalStore {
    void saveOrder(Order order);

    Optional<Order> findOrder(String orderId);

    Collection<Order> allOrders();

    void saveQuote(QuoteSnapshot quote);

    Optional<QuoteSnapshot> findQuote(String quoteId);

    void saveDriverSession(DriverSessionState sessionState);

    Optional<DriverSessionState> findDriverSession(String driverId);

    Collection<DriverSessionState> allDriverSessions();

    void bindOfferBatch(String orderId, String offerBatchId);

    String offerBatchForOrder(String orderId);

    record QuoteSnapshot(
            String quoteId,
            String customerId,
            String serviceTier,
            double straightLineDistanceKm,
            double estimatedFee,
            int estimatedEtaMinutes
    ) {}
}
