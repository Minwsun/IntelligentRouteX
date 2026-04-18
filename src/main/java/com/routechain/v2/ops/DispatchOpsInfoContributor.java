package com.routechain.v2.ops;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

public final class DispatchOpsInfoContributor implements InfoContributor {
    private final DispatchOpsReadinessService readinessService;

    public DispatchOpsInfoContributor(DispatchOpsReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("dispatchV2Readiness", readinessService.snapshotDetails());
    }
}
