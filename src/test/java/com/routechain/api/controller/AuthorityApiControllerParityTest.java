package com.routechain.api.controller;

import com.jayway.jsonpath.JsonPath;
import com.routechain.api.RouteChainApiApplication;
import com.routechain.api.dto.DriverLoginRequest;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.api.service.UserOrderingService;
import com.routechain.infra.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = RouteChainApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "routechain.persistence.jdbc.enabled=false",
                "management.health.redis.enabled=false"
        }
)
@AutoConfigureMockMvc
class AuthorityApiControllerParityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DriverOperationsService driverOperationsService;

    @Autowired
    private UserOrderingService userOrderingService;

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clear();
    }

    @Test
    void acceptedOrderShouldExposeSameAuthorityTruthAcrossAllHttpSurfaces() throws Exception {
        driverOperationsService.login(new DriverLoginRequest("drv-http-parity", "device-http-parity", 10.7765, 106.7009));
        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-http-parity",
                "pickup-r1",
                "drop-r9",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-http-parity"
        ), "idem-http-parity-order");
        String offerId = driverOperationsService.offers("drv-http-parity").getFirst().offerId();
        driverOperationsService.accept("drv-http-parity", offerId, "idem-http-parity-accept");

        String userOrder = mockMvc.perform(get("/v1/user/orders/{orderId}", created.orderId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String userTracking = mockMvc.perform(get("/v1/user/orders/{orderId}/tracking", created.orderId()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String driverTask = mockMvc.perform(get("/v1/driver/tasks/active").param("driverId", "drv-http-parity"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String merchantOrders = mockMvc.perform(get("/v1/merchant/orders").param("merchantId", "merchant-http-parity"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String opsOrders = mockMvc.perform(get("/v1/ops/orders/active"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userLifecycle = JsonPath.parse(userOrder).read("$.lifecycleStage");
        String trackingLifecycle = JsonPath.parse(userTracking).read("$.lifecycleStage");
        String driverLifecycle = JsonPath.parse(driverTask).read("$.lifecycleStage");
        String merchantLifecycle = JsonPath.parse(merchantOrders).read("$[0].lifecycleStage");
        String opsLifecycle = JsonPath.parse(opsOrders).read("$[0].lifecycleStage");

        String userOfferStage = JsonPath.parse(userOrder).read("$.offerSnapshot.stage");
        String trackingOfferStage = JsonPath.parse(userTracking).read("$.offerSnapshot.stage");
        String merchantOfferStage = JsonPath.parse(merchantOrders).read("$[0].offerSnapshot.stage");
        String opsOfferStage = JsonPath.parse(opsOrders).read("$[0].offerSnapshot.stage");

        assertEquals("ACCEPTED", userLifecycle);
        assertEquals(userLifecycle, trackingLifecycle);
        assertEquals(userLifecycle, driverLifecycle);
        assertEquals(userLifecycle, merchantLifecycle);
        assertEquals(userLifecycle, opsLifecycle);

        assertEquals("LOCKED_ASSIGNMENT", userOfferStage);
        assertEquals(userOfferStage, trackingOfferStage);
        assertEquals(userOfferStage, merchantOfferStage);
        assertEquals(userOfferStage, opsOfferStage);
    }
}
