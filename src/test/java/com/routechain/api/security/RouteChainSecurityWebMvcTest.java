package com.routechain.api.security;

import com.routechain.api.controller.DriverController;
import com.routechain.api.controller.OpsController;
import com.routechain.api.controller.UserOrderController;
import com.routechain.api.http.CorrelationIdFilter;
import com.routechain.api.http.RouteChainExceptionHandler;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.api.service.OpsArtifactService;
import com.routechain.api.service.UserOrderingService;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.data.service.WalletQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                UserOrderController.class,
                DriverController.class,
                OpsController.class
        },
        properties = "routechain.security.enabled=true"
)
@Import({
        RouteChainSecurityConfiguration.class,
        RouteChainAuthenticationEntryPoint.class,
        RouteChainAccessDeniedHandler.class,
        RouteChainJwtAuthoritiesConverter.class,
        RouteChainExceptionHandler.class,
        CorrelationIdFilter.class,
        ActorAccessGuard.class
})
class RouteChainSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserOrderingService userOrderingService;

    @MockBean
    private DriverOperationsService driverOperationsService;

    @MockBean
    private WalletQueryService walletQueryService;

    @MockBean
    private OpsArtifactService opsArtifactService;

    @MockBean
    private OfferStateStore offerStateStore;

    @MockBean
    private OperationalEventPublisher operationalEventPublisher;

    @MockBean
    private OfferBrokerService offerBrokerService;

    @Test
    void anonymousProtectedRequestReturnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/v1/driver/offers").param("driverId", "drv-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void mismatchedDriverSubjectReturnsForbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/v1/driver/offers")
                        .param("driverId", "drv-1")
                        .with(jwt().jwt(jwt -> jwt.subject("drv-2"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access_denied"));
    }

    @Test
    void matchingDriverSubjectCanReadOffers() throws Exception {
        when(driverOperationsService.offers("drv-1")).thenReturn(List.of());

        mockMvc.perform(get("/v1/driver/offers")
                        .param("driverId", "drv-1")
                        .with(jwt().jwt(jwt -> jwt.subject("drv-1"))))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void opsEndpointRejectsAuthenticatedNonOpsRole() throws Exception {
        mockMvc.perform(get("/v1/ops/control-room/frame/latest")
                        .with(jwt().jwt(jwt -> jwt.subject("drv-ops"))
                                .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
                .andExpect(status().isForbidden());
    }
}
