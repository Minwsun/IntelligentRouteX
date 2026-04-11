package com.routechain.api.controller;

import com.routechain.api.dto.DriverTaskStatusUpdate;
import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.data.service.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverController.class)
@AutoConfigureMockMvc(addFilters = false)
class DriverControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DriverOperationsService driverOperationsService;

    @MockBean
    private WalletQueryService walletQueryService;

    @MockBean
    private ActorAccessGuard actorAccessGuard;

    @MockBean
    private OfferStateStore offerStateStore;

    @MockBean
    private OperationalEventPublisher operationalEventPublisher;

    @MockBean
    private OfferBrokerService offerBrokerService;

    @Test
    void loginRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/v1/driver/session/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId": "",
                                  "deviceId": "device-1",
                                  "lat": 10.775,
                                  "lng": 106.701
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTaskStatusMapsMissingOrderToNotFound() throws Exception {
        when(actorAccessGuard.currentSubject()).thenReturn("drv-1");
        when(driverOperationsService.updateTaskStatus("drv-1", "task-missing", new DriverTaskStatusUpdate("DELIVERED")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/driver/tasks/task-missing/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "DELIVERED" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTaskStatusSupportsDriverIdFallbackForDemoMode() throws Exception {
        when(driverOperationsService.updateTaskStatus("drv-demo", "task-missing", new DriverTaskStatusUpdate("DELIVERED")))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/driver/tasks/task-missing/status?driverId=drv-demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "DELIVERED" }
                                """))
                .andExpect(status().isNotFound());
    }
}
