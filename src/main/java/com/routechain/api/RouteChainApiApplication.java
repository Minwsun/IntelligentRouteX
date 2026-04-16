package com.routechain.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.routechain")
@ConfigurationPropertiesScan(basePackages = "com.routechain.config")
public class RouteChainApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouteChainApiApplication.class, args);
    }
}

