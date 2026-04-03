package com.routechain.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverHeartbeatRequest(@NotBlank String driverId) {}
