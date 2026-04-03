package com.routechain.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserOrderRequest(
        @NotBlank String customerId,
        @NotBlank String pickupRegionId,
        @NotBlank String dropoffRegionId,
        @NotNull Double pickupLat,
        @NotNull Double pickupLng,
        @NotNull Double dropoffLat,
        @NotNull Double dropoffLng,
        @NotBlank String serviceTier,
        int promisedEtaMinutes,
        String merchantId
) {}
