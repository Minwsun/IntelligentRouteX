package com.routechain.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverTaskStatusUpdate(@NotBlank String status) {}
