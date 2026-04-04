package com.routechain.api;

import com.routechain.config.RouteChainRuntimeProperties;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferRuntimeStore;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mobile/backend API entry point. Runs independently from the JavaFX control-room.
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
                                                 OperationalEventPublisher eventPublisher,
                                                 RouteChainRuntimeProperties runtimeProperties) {
        return new OfferBrokerService(
                offerStateStore,
                offerRuntimeStore,
                eventPublisher,
                runtimeProperties.getOffers().getDeclineCooldown(),
                runtimeProperties.getOffers().getExpiryCooldown()
        );
    }
}
