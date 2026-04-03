package com.routechain.api.dto;

import jakarta.validation.constraints.NotNull;

public record DriverLocationUpdate(
        @NotNull Double lat,
        @NotNull Double lng,
        double speedKmh
) {}
