package com.routechain.api;

import com.routechain.config.RouteChainRuntimeProperties;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferRuntimeStore;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OrderLifecycleFactService;
import com.routechain.data.service.OperationalEventPublisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mobile/backend transport entry point.
 *
 * This process exposes API surfaces for mobile clients and operational access,
 * but it must not become a second visual runtime or a second live dispatch
 * core. The authoritative live control-room remains the JavaFX desktop app.
 */
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "com.routechain")
@SpringBootApplication(scanBasePackages = "com.routechain", exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
public class RouteChainApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteChainApiApplication.class, args);
    }

    @Bean
    public OfferBrokerService offerBrokerService(OfferStateStore offerStateStore,
                                                 OfferRuntimeStore offerRuntimeStore,
                                                 OrderLifecycleFactService lifecycleFactService,
                                                 OperationalEventPublisher eventPublisher,
                                                 RouteChainRuntimeProperties runtimeProperties) {
        return new OfferBrokerService(
                offerStateStore,
                offerRuntimeStore,
                lifecycleFactService,
                eventPublisher,
                runtimeProperties.getOffers().getDeclineCooldown(),
                runtimeProperties.getOffers().getExpiryCooldown()
        );
    }
}
