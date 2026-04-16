package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairFeatureBuilderTest {

    @Test
    void computesDistanceEtaReadyGapAndWeatherTighteningDeterministically() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        BaselineTravelTimeEstimator baselineTravelTimeEstimator = new BaselineTravelTimeEstimator();
        PairFeatureBuilder pairFeatureBuilder = new PairFeatureBuilder(baselineTravelTimeEstimator);
        EtaService etaService = new EtaService(
                properties,
                baselineTravelTimeEstimator,
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        EtaLegCache etaLegCache = new EtaLegCache(
                etaService,
                ClusterTestFixtures.request(List.of()),
                properties.getPair().getMlTimeout().toMillis());
        Order left = ClusterTestFixtures.order("order-1", 10.7750, 106.7000, 10.7820, 106.7100, "2026-04-16T12:00:00Z", false);
        Order right = ClusterTestFixtures.order("order-2", 10.7760, 106.7010, 10.7830, 106.7110, "2026-04-16T12:05:00Z", false);
        BufferedOrderWindow window = ClusterTestFixtures.window(List.of(left, right));

        PairFeatureVector first = pairFeatureBuilder.build(window, left, right, ClusterTestFixtures.weatherBadEtaContext(), etaLegCache);
        PairFeatureVector second = pairFeatureBuilder.build(window, left, right, ClusterTestFixtures.weatherBadEtaContext(), etaLegCache);

        assertTrue(first.pickupDistanceKm() > 0.0);
        assertTrue(first.pickupEtaMinutes() > 0.0);
        assertEquals(5L, first.readyGapMinutes());
        assertTrue(first.weatherTightened());
        assertEquals(first, second);
    }

    @Test
    void leavesWeatherTightenedFalseWhenEtaContextIsClear() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        PairFeatureBuilder pairFeatureBuilder = new PairFeatureBuilder(new BaselineTravelTimeEstimator());
        Order left = ClusterTestFixtures.order("order-1", 10.7750, 106.7000, 10.7820, 106.7100, "2026-04-16T12:00:00Z", false);
        Order right = ClusterTestFixtures.order("order-2", 10.7760, 106.7010, 10.7830, 106.7110, "2026-04-16T12:05:00Z", false);
        EtaService etaService = new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        EtaLegCache etaLegCache = new EtaLegCache(
                etaService,
                ClusterTestFixtures.request(List.of()),
                properties.getPair().getMlTimeout().toMillis());

        PairFeatureVector vector = pairFeatureBuilder.build(
                ClusterTestFixtures.window(List.of(left, right)),
                left,
                right,
                ClusterTestFixtures.clearEtaContext(),
                etaLegCache);

        assertFalse(vector.weatherTightened());
    }
}
