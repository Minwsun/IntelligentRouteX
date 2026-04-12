package com.routechain.core;

public record CompactSelectionAudit(
        String driverId,
        boolean batchEligible,
        boolean batchChosen,
        CompactPlanType selectedPlanType,
        String selectedTraceId,
        String reason) {
}
