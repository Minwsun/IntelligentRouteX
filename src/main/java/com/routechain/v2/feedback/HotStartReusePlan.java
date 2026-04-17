package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.bundle.BundleReuseInput;
import com.routechain.v2.cluster.PairClusterReuseInput;
import com.routechain.v2.route.RouteProposalReuseInput;

import java.util.List;

public record HotStartReusePlan(
        String schemaVersion,
        String previousTraceId,
        boolean reuseEligible,
        boolean pairClusterReusePlanned,
        boolean bundleReusePlanned,
        boolean routeProposalReusePlanned,
        DispatchRuntimeReuseState reuseState,
        List<String> degradeReasons) implements SchemaVersioned {

    public static HotStartReusePlan none() {
        return new HotStartReusePlan(
                "hot-start-reuse-plan/v1",
                null,
                false,
                false,
                false,
                false,
                null,
                List.of());
    }

    public PairClusterReuseInput pairClusterReuseInput() {
        return pairClusterReusePlanned && reuseState != null ? new PairClusterReuseInput("pair-cluster-reuse-input/v1", reuseState) : null;
    }

    public BundleReuseInput bundleReuseInput() {
        return bundleReusePlanned && reuseState != null ? new BundleReuseInput("bundle-reuse-input/v1", reuseState) : null;
    }

    public RouteProposalReuseInput routeProposalReuseInput() {
        return routeProposalReusePlanned && reuseState != null ? new RouteProposalReuseInput("route-proposal-reuse-input/v1", reuseState) : null;
    }
}
