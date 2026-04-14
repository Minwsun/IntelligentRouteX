package com.routechain.api.controller;

import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantController.class)
@AutoConfigureMockMvc(addFilters = false)
class MerchantControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuntimeBridge runtimeBridge;

    @MockBean
    private ActorAccessGuard actorAccessGuard;

    @MockBean
    private OfferStateStore offerStateStore;

    @MockBean
    private OperationalEventPublisher operationalEventPublisher;

    @MockBean
    private OfferBrokerService offerBrokerService;

    @Test
    void ordersRequiresMerchantIdParameter() throws Exception {
        mockMvc.perform(get("/v1/merchant/orders"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersReturnsOkForKnownMerchant() throws Exception {
        when(runtimeBridge.merchantOrders("merchant-1")).thenReturn(List.of());

        mockMvc.perform(get("/v1/merchant/orders").param("merchantId", "merchant-1"))
                .andExpect(status().isOk());
    }
}
