package com.routechain.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketJwtHandshakeInterceptorTest {

    @Test
    void driverAudienceAcceptsMatchingSubject() throws Exception {
        WebSocketJwtHandshakeInterceptor interceptor = newInterceptor(true, jwt("drv-1", List.of("driver")));
        ServerHttpRequest request = request("/v1/driver/stream?driverId=drv-1", true);
        ServerHttpResponse response = response();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.DRIVER)
                .beforeHandshake(request, response, new NoOpWebSocketHandler(), attributes);

        assertTrue(accepted);
        assertEquals("drv-1", attributes.get("jwtSubject"));
    }

    @Test
    void preferredUsernameClaimCanDriveActorMatching() throws Exception {
        RouteChainSecurityProperties properties = new RouteChainSecurityProperties();
        properties.setEnabled(true);
        properties.setActorIdClaim("preferred_username");
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("token-driver")).thenReturn(jwt("uuid-subject", "driver-demo", List.of("driver")));
        WebSocketJwtHandshakeInterceptor interceptor = new WebSocketJwtHandshakeInterceptor(
                properties,
                decoder,
                new RouteChainJwtAuthoritiesConverter(),
                new JwtActorIdentityResolver(properties)
        );

        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = interceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.DRIVER)
                .beforeHandshake(
                        request("/v1/driver/stream?driverId=driver-demo", true),
                        response(),
                        new NoOpWebSocketHandler(),
                        attributes
                );

        assertTrue(accepted);
        assertEquals("driver-demo", attributes.get("jwtSubject"));
    }

    @Test
    void driverAudienceRejectsMismatchedSubject() throws Exception {
        WebSocketJwtHandshakeInterceptor interceptor = newInterceptor(true, jwt("drv-2", List.of("driver")));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        boolean accepted = interceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.DRIVER)
                .beforeHandshake(
                        request("/v1/driver/stream?driverId=drv-1", true),
                        response,
                        new NoOpWebSocketHandler(),
                        new HashMap<>()
                );

        assertFalse(accepted);
        assertEquals(HttpStatus.FORBIDDEN.value(), servletResponse.getStatus());
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        WebSocketJwtHandshakeInterceptor interceptor = newInterceptor(true, jwt("drv-1", List.of("driver")));
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        boolean accepted = interceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.DRIVER)
                .beforeHandshake(
                        request("/v1/driver/stream?driverId=drv-1", false),
                        response,
                        new NoOpWebSocketHandler(),
                        new HashMap<>()
                );

        assertFalse(accepted);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
    }

    private WebSocketJwtHandshakeInterceptor newInterceptor(boolean enabled, Jwt jwt) {
        RouteChainSecurityProperties properties = new RouteChainSecurityProperties();
        properties.setEnabled(enabled);
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("token-driver")).thenReturn(jwt);
        return new WebSocketJwtHandshakeInterceptor(
                properties,
                decoder,
                new RouteChainJwtAuthoritiesConverter(),
                new JwtActorIdentityResolver(properties)
        );
    }

    private Jwt jwt(String subject, List<String> roles) {
        return jwt(subject, null, roles);
    }

    private Jwt jwt(String subject, String preferredUsername, List<String> roles) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                claims(subject, preferredUsername, roles)
        );
    }

    private Map<String, Object> claims(String subject, String preferredUsername, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("realm_access", Map.of("roles", roles));
        if (preferredUsername != null) {
            claims.put("preferred_username", preferredUsername);
        }
        return claims;
    }

    private ServerHttpRequest request(String uri, boolean withBearerToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (withBearerToken) {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-driver");
        }
        return new ServletServerHttpRequest(request);
    }

    private ServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    private static final class NoOpWebSocketHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        }
    }
}
