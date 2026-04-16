package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.OpenMeteoClient;
import com.routechain.v2.integration.TabularScoringClient;
import com.routechain.v2.integration.TomTomTrafficRefineClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DispatchV2Configuration {

    @Bean
    BaselineTravelTimeEstimator baselineTravelTimeEstimator() {
        return new BaselineTravelTimeEstimator();
    }

    @Bean
    TrafficProfileService trafficProfileService(RouteChainDispatchV2Properties properties) {
        return new TrafficProfileService(properties);
    }

    @Bean
    OpenMeteoClient openMeteoClient() {
        return new NoOpOpenMeteoClient();
    }

    @Bean
    WeatherContextService weatherContextService(RouteChainDispatchV2Properties properties, OpenMeteoClient openMeteoClient) {
        return new WeatherContextService(properties, openMeteoClient);
    }

    @Bean
    TomTomTrafficRefineClient tomTomTrafficRefineClient() {
        return new NoOpTomTomTrafficRefineClient();
    }

    @Bean
    TabularScoringClient tabularScoringClient() {
        return new NoOpTabularScoringClient();
    }

    @Bean
    EtaFeatureBuilder etaFeatureBuilder() {
        return new EtaFeatureBuilder();
    }

    @Bean
    EtaUncertaintyEstimator etaUncertaintyEstimator() {
        return new EtaUncertaintyEstimator();
    }

    @Bean
    EtaService etaService(RouteChainDispatchV2Properties properties,
                          BaselineTravelTimeEstimator baselineTravelTimeEstimator,
                          TrafficProfileService trafficProfileService,
                          WeatherContextService weatherContextService,
                          TomTomTrafficRefineClient tomTomTrafficRefineClient,
                          TabularScoringClient tabularScoringClient,
                          EtaFeatureBuilder etaFeatureBuilder,
                          EtaUncertaintyEstimator etaUncertaintyEstimator) {
        return new EtaService(
                properties,
                baselineTravelTimeEstimator,
                trafficProfileService,
                weatherContextService,
                tomTomTrafficRefineClient,
                tabularScoringClient,
                etaFeatureBuilder,
                etaUncertaintyEstimator);
    }

    @Bean
    DispatchEtaContextService dispatchEtaContextService(RouteChainDispatchV2Properties properties, EtaService etaService) {
        return new DispatchEtaContextService(properties, etaService);
    }

    @Bean
    DispatchV2Core dispatchV2Core(DispatchEtaContextService dispatchEtaContextService) {
        return new DispatchV2Core(dispatchEtaContextService);
    }

    @Bean
    DispatchV2CompatibleCore dispatchV2CompatibleCore(RouteChainDispatchV2Properties properties, DispatchV2Core dispatchV2Core) {
        return new DispatchV2CompatibleCore(properties, dispatchV2Core);
    }
}

