package com.routechain.v2.executor;

import com.routechain.v2.SchemaVersioned;

import java.time.Instant;
import java.util.List;

public record DispatchAssignment(
        String schemaVersion,
        String proposalId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        List<String> orderIds,
        List<String> stopOrder,
        ExecutionActionType actionType,
        int selectionRank,
        double selectionScore,
        double robustUtility,
        double routeValue,
        String clusterId,
        boolean boundaryCross,
        Instant readyWindowStart,
        Instant readyWindowEnd,
        List<String> reasons,
        List<String> degradeReasons) implements SchemaVersioned {
}
