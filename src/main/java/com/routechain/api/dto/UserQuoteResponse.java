package com.routechain.api.dto;

public record UserQuoteResponse(
        String quoteId,
        String customerId,
        String serviceTier,
        double straightLineDistanceKm,
        double estimatedFee,
        int estimatedEtaMinutes
) {}
