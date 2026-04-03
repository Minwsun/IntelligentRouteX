package com.routechain.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteChainApiApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void actuatorAndSwaggerEndpointsAreAvailable() {
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class).getStatusCode());
        assertEquals(HttpStatus.OK,
                restTemplate.getForEntity("http://localhost:" + port + "/swagger-ui.html", String.class).getStatusCode());
    }
}
