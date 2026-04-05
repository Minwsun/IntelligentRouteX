package com.routechain.api.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WebSocketJwtHandshakeInterceptor implements HandshakeInterceptor {
    public enum Audience {
        DRIVER,
        USER,
        OPS
    }

    private final RouteChainSecurityProperties securityProperties;
    private final JwtDecoder jwtDecoder;
    private final RouteChainJwtAuthoritiesConverter authoritiesConverter;
    private final JwtActorIdentityResolver actorIdentityResolver;

    public WebSocketJwtHandshakeInterceptor(RouteChainSecurityProperties securityProperties,
                                            JwtDecoder jwtDecoder,
                                            RouteChainJwtAuthoritiesConverter authoritiesConverter,
                                            JwtActorIdentityResolver actorIdentityResolver) {
        this.securityProperties = securityProperties;
        this.jwtDecoder = jwtDecoder;
        this.authoritiesConverter = authoritiesConverter;
        this.actorIdentityResolver = actorIdentityResolver;
    }

    public HandshakeInterceptor forAudience(Audience audience) {
        return new AudienceSpecificHandshakeInterceptor(audience);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private final class AudienceSpecificHandshakeInterceptor implements HandshakeInterceptor {
        private final Audience audience;

        private AudienceSpecificHandshakeInterceptor(Audience audience) {
            this.audience = audience;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request,
                                       ServerHttpResponse response,
                                       WebSocketHandler wsHandler,
                                       Map<String, Object> attributes) {
            if (!securityProperties.isEnabled()) {
                return true;
            }
            try {
                Jwt jwt = jwtDecoder.decode(resolveToken(request));
                Set<String> roles = authoritiesConverter.extractRoles(jwt);
                String actorId = actorIdentityResolver.resolveActorId(jwt);
                if (!isAuthorized(request, actorId, roles)) {
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    return false;
                }
                attributes.put("jwtSubject", actorId);
                attributes.put("jwtRoles", roles);
                return true;
            } catch (Exception ex) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Exception exception) {
        }

        private boolean isAuthorized(ServerHttpRequest request, String actorId, Set<String> roles) {
            return switch (audience) {
                case DRIVER -> actorId.equals(queryParam(request, "driverId")) || hasOpsRole(roles);
                case USER -> actorId.equals(queryParam(request, "customerId")) || hasOpsRole(roles);
                case OPS -> hasOpsRole(roles);
            };
        }
    }

    private boolean hasOpsRole(Collection<String> roles) {
        return roles.contains("OPS") || roles.contains("ADMIN")
                || roles.contains("ops") || roles.contains("admin");
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        String queryToken = queryParam(request, "access_token");
        if (!queryToken.isBlank()) {
            return queryToken;
        }
        throw new IllegalArgumentException("Missing access token");
    }

    private String queryParam(ServerHttpRequest request, String key) {
        Map<String, List<String>> queryParams = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        List<String> values = queryParams.get(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}
