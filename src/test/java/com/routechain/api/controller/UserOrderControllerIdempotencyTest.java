package com.routechain.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.data.port.OfferStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "routechain.persistence.jdbc.enabled=false",
                "management.health.redis.enabled=false"
        }
)
@AutoConfigureMockMvc
class UserOrderControllerIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryOperationalStore store;

    @Autowired
    private OfferStateStore offerStateStore;

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
    }

    @Test
    void repeatedCreateOrderWithSameIdempotencyKeyReturnsSameResponse() throws Exception {
        store.saveDriverSession(new DriverSessionState("drv-idem", "device-1", true, 10.775, 106.701, Instant.now(), ""));

        String requestBody = """
                {
                  "customerId": "cust-idem",
                  "pickupRegionId": "pickup-r1",
                  "dropoffRegionId": "drop-r9",
                  "pickupLat": 10.776,
                  "pickupLng": 106.700,
                  "dropoffLat": 10.780,
                  "dropoffLng": 106.710,
                  "serviceTier": "instant",
                  "promisedEtaMinutes": 25,
                  "merchantId": "merchant-1"
                }
                """;

        String first = mockMvc.perform(post("/v1/user/orders")
                        .header("Idempotency-Key", "idem-user-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/v1/user/orders")
                        .header("Idempotency-Key", "idem-user-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstOrderId = JsonPath.parse(first).read("$.orderId");
        String secondOrderId = JsonPath.parse(second).read("$.orderId");
        String firstOfferBatchId = JsonPath.parse(first).read("$.offerBatchId");
        String secondOfferBatchId = JsonPath.parse(second).read("$.offerBatchId");

        assertEquals(firstOrderId, secondOrderId);
        assertEquals(firstOfferBatchId, secondOfferBatchId);
        assertEquals(1, store.allOrders().size());
        assertTrue(offerStateStore.latestBatchForOrder(firstOrderId).isPresent());
    }
}
