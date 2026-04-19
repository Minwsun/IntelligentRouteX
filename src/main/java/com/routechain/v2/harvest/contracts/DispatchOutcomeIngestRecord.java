package com.routechain.v2.harvest.contracts;

import java.time.Instant;
import java.util.Map;

public record DispatchOutcomeIngestRecord(
        String schemaVersion,
        String assignmentId,
        String proposalId,
        String traceId,
        Map<String, Object> businessKeys,
        Double actualPickupTravelMinutes,
        Double actualMerchantWaitMinutes,
        Double actualDropoffTravelMinutes,
        Double actualTotalCompletionMinutes,
        Double realizedTrafficDelayMinutes,
        Double realizedWeatherModifier,
        boolean delivered,
        String outcomeSource,
        Instant reconciledAt) {
}
