package com.routechain.simulator.logging;

import java.time.Instant;

public record DispatchOutcomeRecord(
        String schemaVersion,
        String runId,
        String sliceId,
        int worldIndex,
        String traceId,
        String orderId,
        Instant deliveredAt,
        long actualPickupTravelSeconds,
        long actualMerchantWaitSeconds,
        long actualDropoffTravelSeconds,
        long actualTotalCompletionSeconds,
        long realizedTrafficDelaySeconds,
        String realizedWeatherModifier,
        boolean delivered) {
}
