package com.routechain.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ActorAccessGuard {
    private final RouteChainSecurityProperties securityProperties;
    private final JwtActorIdentityResolver actorIdentityResolver;

    public ActorAccessGuard(RouteChainSecurityProperties securityProperties,
                            JwtActorIdentityResolver actorIdentityResolver) {
        this.securityProperties = securityProperties;
        this.actorIdentityResolver = actorIdentityResolver;
    }

    public void requireCustomer(String customerId) {
        if (!securityProperties.isEnabled()) {
            return;
        }
        String subject = currentSubject();
        if (hasAnyRole("OPS", "ADMIN")) {
            return;
        }
        if (!subject.equals(customerId)) {
            throw new AccessDeniedException("JWT subject does not match customerId");
        }
    }

    public void requireDriver(String driverId) {
        if (!securityProperties.isEnabled()) {
            return;
        }
        String subject = currentSubject();
        if (hasAnyRole("OPS", "ADMIN")) {
            return;
        }
        if (!subject.equals(driverId)) {
            throw new AccessDeniedException("JWT subject does not match driverId");
        }
    }

    public void requireMerchant(String merchantId) {
        if (!securityProperties.isEnabled()) {
            return;
        }
        String subject = currentSubject();
        if (hasAnyRole("OPS", "ADMIN")) {
            return;
        }
        if (!subject.equals(merchantId)) {
            throw new AccessDeniedException("JWT subject does not match merchantId");
        }
    }

    public void requireOps() {
        if (!securityProperties.isEnabled()) {
            return;
        }
        if (!hasAnyRole("OPS", "ADMIN")) {
            throw new AccessDeniedException("Ops role is required");
        }
    }

    public String currentSubject() {
        if (!securityProperties.isEnabled()) {
            return "";
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            return actorIdentityResolver.resolveActorId(jwt);
        }
        throw new AccessDeniedException("Authenticated JWT subject is required");
    }

    public boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        for (String role : roles) {
            if (authorities.contains("ROLE_" + role)) {
                return true;
            }
        }
        return false;
    }
}
