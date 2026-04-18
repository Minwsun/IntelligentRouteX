package com.routechain.v2.certification;

import com.routechain.v2.DispatchV2Result;

public record DispatchHotStartCertificationRun(
        DispatchHotStartCertificationReport report,
        DispatchV2Result coldResult,
        DispatchV2Result warmResult,
        DispatchV2Result hotResult) {
}
