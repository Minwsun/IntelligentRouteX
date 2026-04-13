package com.routechain.api.dto;

public record AssignmentLockView(
        String reservationId,
        String driverId,
        String acceptedOfferId,
        long reservationVersion,
        String status,
        String reservedAt,
        String expiresAt
) {}
