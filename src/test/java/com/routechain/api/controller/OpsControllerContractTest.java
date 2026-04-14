package com.routechain.api.controller;

import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.OpsArtifactService;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.infra.AdminQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpsController.class)
@AutoConfigureMockMvc(addFilters = false)
class OpsControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpsArtifactService opsArtifactService;

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
    void latestFrameReturnsOkEnvelope() throws Exception {
        when(opsArtifactService.adminSnapshot()).thenReturn(new AdminQueryService.SystemAdminSnapshot(
                Instant.parse("2026-04-15T00:00:00Z"),
                "dispatch-brain-1",
                List.of(),
                Map.of(),
                List.of(),
                "NORMAL",
                "OFFLINE",
                AdminQueryService.LlmRuntimeStatus.offlineDefault("OFFLINE"),
                "run-1",
                "scenario-a",
                "PASS"));
        when(opsArtifactService.latestControlRoomMarkdown()).thenReturn("# control-room");

        mockMvc.perform(get("/v1/ops/control-room/frame/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controlRoomMarkdown").value("# control-room"));
    }

    @Test
    void activeOrdersReturnsArray() throws Exception {
        when(runtimeBridge.opsRealtimeSnapshot()).thenReturn(new com.routechain.api.dto.OpsRealtimeSnapshot(List.of()));

        mockMvc.perform(get("/v1/ops/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
