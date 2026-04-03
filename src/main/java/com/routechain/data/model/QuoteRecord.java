package com.routechain.data.model;

import java.time.Instant;

/**
 * Quote snapshot persisted for API and audit flows.
 */
public record QuoteRecord(
        String quoteId,
        String customerId,
        String serviceTier,
        double straightLineDistanceKm,
        double estimatedFee,
        int estimatedEtaMinutes,
        Instant createdAt
) {
    public QuoteRecord {
        quoteId = quoteId == null || quoteId.isBlank() ? "quote-unknown" : quoteId;
        customerId = customerId == null ? "" : customerId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
