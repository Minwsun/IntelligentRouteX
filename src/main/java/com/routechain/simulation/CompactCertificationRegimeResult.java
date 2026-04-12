package com.routechain.simulation;

import java.util.List;

public record CompactCertificationRegimeResult(
        String regime,
        int sampleCount,
        double completionDeltaVsOmega,
        double onTimeDeltaVsOmega,
        double deadheadDeltaVsOmega,
        double emptyKmDeltaVsOmega,
        double postDropHitDeltaVsOmega,
        boolean pass,
        List<String> notes) {
}
