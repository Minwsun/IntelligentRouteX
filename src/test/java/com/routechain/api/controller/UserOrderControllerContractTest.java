package com.routechain.api.controller;

import com.routechain.api.dto.UserOrderResponse;
import com.routechain.api.service.UserOrderingService;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.data.service.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserOrderController.class)
class UserOrderControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserOrderingService userOrderingService;

    @MockBean
    private WalletQueryService walletQueryService;

    @MockBean
    private OfferStateStore offerStateStore;

    @MockBean
    private OperationalEventPublisher operationalEventPublisher;

    @Test
    void createOrderRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/v1/user/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "",
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
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrderMapsMissingOrderToNotFound() throws Exception {
        when(userOrderingService.order("missing-order")).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/user/orders/missing-order"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrderMapsMissingOrderToNotFound() throws Exception {
        when(userOrderingService.cancel("missing-order", "user_cancelled", null)).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/user/orders/missing-order/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
