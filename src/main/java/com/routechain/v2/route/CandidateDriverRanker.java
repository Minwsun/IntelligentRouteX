package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.context.EtaEstimate;
import com.routechain.v2.context.EtaService;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class CandidateDriverRanker {
    private final RouteChainDispatchV2Properties.Candidate properties;
    private final EtaService etaService;

    public CandidateDriverRanker(RouteChainDispatchV2Properties.Candidate properties, EtaService etaService) {
        this.properties = properties;
        this.etaService = etaService;
    }

    public List<CandidateDriverMatch> rank(BundleCandidate bundle,
                                           PickupAnchor anchor,
                                           List<Driver> availableDrivers,
                                           Instant decisionTime,
                                           com.routechain.domain.Enums.WeatherProfile weatherProfile,
                                           double trafficIntensity) {
        return availableDrivers.stream()
                .map(driver -> toCandidate(bundle, anchor, driver, decisionTime, weatherProfile, trafficIntensity))
                .sorted(Comparator.comparingDouble(
                        (CandidateDriverMatch match) -> match.reachEta().etaMinutes()))
                .limit(maxDrivers())
                .sorted(Comparator.comparingDouble(CandidateDriverMatch::score).reversed())
                .map(new RankAssigner())
                .toList();
    }

    private CandidateDriverMatch toCandidate(BundleCandidate bundle,
                                             PickupAnchor anchor,
                                             Driver driver,
                                             Instant decisionTime,
                                             com.routechain.domain.Enums.WeatherProfile weatherProfile,
                                             double trafficIntensity) {
        EtaEstimate reachEta = etaService.estimate(
                driver.getCurrentLocation(),
                anchor.location(),
                decisionTime,
                weatherProfile,
                trafficIntensity,
                true,
                anchor.anchorOrder().getServiceType());
        double reachEtaQuality = clamp01(1.0 - reachEta.etaMinutes() / 20.0);
        double onTimeSafety = clamp01(bundle.bundleScore().slaSafety());
        double lowDeadhead = clamp01(1.0 - driver.getCurrentLocation().distanceTo(anchor.location()) / 5_000.0);
        double routeValue = clamp01(bundle.bundleScore().totalScore());
        double landingValue = clamp01(bundle.bundleScore().landingValue());
        double stability = clamp01(1.0 - reachEta.etaUncertainty());
        double score = 0.30 * reachEtaQuality
                + 0.18 * onTimeSafety
                + 0.14 * lowDeadhead
                + 0.14 * routeValue
                + 0.10 * landingValue
                + 0.14 * stability;
        return new CandidateDriverMatch(driver, -1, reachEta, clamp01(score));
    }

    private int maxDrivers() {
        return properties == null ? 8 : properties.getMaxDrivers();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class RankAssigner implements java.util.function.Function<CandidateDriverMatch, CandidateDriverMatch> {
        private int rank = 0;

        @Override
        public CandidateDriverMatch apply(CandidateDriverMatch match) {
            return new CandidateDriverMatch(match.driver(), ++rank, match.reachEta(), match.score());
        }
    }
}
