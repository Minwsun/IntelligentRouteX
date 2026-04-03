package com.routechain.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserQuoteRequest(
        @NotBlank String customerId,
        @NotBlank String pickupRegionId,
        @NotBlank String dropoffRegionId,
        @NotNull Double pickupLat,
        @NotNull Double pickupLng,
        @NotNull Double dropoffLat,
        @NotNull Double dropoffLng,
        @NotBlank String serviceTier,
        @Min(10) @Max(240) int promisedEtaMinutes
) {}
