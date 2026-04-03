package com.routechain.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DriverLoginRequest(
        @NotBlank String driverId,
        @NotBlank String deviceId,
        @NotNull Double lat,
        @NotNull Double lng
) {}
