package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DriverCandidate(
        String schemaVersion,
        String bundleId,
        String anchorOrderId,
        String driverId,
        int rank,
        double pickupEtaMinutes,
        double driverFitScore,
        double rerankScore,
        List<String> reasons,
        List<String> degradeReasons) implements SchemaVersioned {
}
