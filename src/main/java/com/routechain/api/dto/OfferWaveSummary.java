package com.routechain.api.dto;

public record OfferWaveSummary(
        String offerBatchId,
        int wave,
        String previousBatchId,
        String stage,
        int fanout,
        int pendingOffers,
        int resolvedOffers,
        String createdAt,
        String expiresAt,
        String closedAt,
        String closeReason
) {}
