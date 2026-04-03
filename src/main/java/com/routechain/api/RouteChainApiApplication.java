package com.routechain.api;

import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Mobile/backend API entry point. Runs independently from the JavaFX control-room.
 */
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
                                                 OperationalEventPublisher eventPublisher) {
        return new OfferBrokerService(offerStateStore, eventPublisher);
    }
}
