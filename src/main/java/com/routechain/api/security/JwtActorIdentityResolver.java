package com.routechain.api.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtActorIdentityResolver {
    private final RouteChainSecurityProperties securityProperties;

    public JwtActorIdentityResolver(RouteChainSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String resolveActorId(Jwt jwt) {
        if (jwt == null) {
            return "";
        }
        String configuredClaim = normalizeClaim(securityProperties.getActorIdClaim());
        String fromConfiguredClaim = stringClaim(jwt, configuredClaim);
        if (!fromConfiguredClaim.isBlank()) {
            return fromConfiguredClaim;
        }
        return stringClaim(jwt, "sub");
    }

    private String normalizeClaim(String claim) {
        return claim == null || claim.isBlank() ? "sub" : claim.trim();
    }

    private String stringClaim(Jwt jwt, String claimName) {
        Object raw = jwt.getClaims().get(claimName);
        if (raw == null) {
            return "";
        }
        String value = raw.toString().trim();
        return value;
    }
}
