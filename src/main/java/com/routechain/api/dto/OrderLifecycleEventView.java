package com.routechain.api.dto;

public record OrderLifecycleEventView(
        OrderLifecycleStage stage,
        String rawStatus,
        String reason,
        String recordedAt
) {}
