package com.routechain.api.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RouteChainJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        extractScopes(jwt).forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        extractRoles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        return authorities;
    }

    public Set<String> extractRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            Object rawRoles = map.get("roles");
            if (rawRoles instanceof Collection<?> collection) {
                for (Object role : collection) {
                    if (role != null && !role.toString().isBlank()) {
                        roles.add(role.toString());
                    }
                }
            }
        }
        Object rawRoles = jwt.getClaims().get("roles");
        if (rawRoles instanceof Collection<?> collection) {
            for (Object role : collection) {
                if (role != null && !role.toString().isBlank()) {
                    roles.add(role.toString());
                }
            }
        }
        return roles;
    }

    private List<String> extractScopes(Jwt jwt) {
        List<String> scopes = new ArrayList<>();
        Object scope = jwt.getClaims().get("scope");
        if (scope instanceof String value && !value.isBlank()) {
            for (String item : value.split("\\s+")) {
                if (!item.isBlank()) {
                    scopes.add(item);
                }
            }
        }
        Object scp = jwt.getClaims().get("scp");
        if (scp instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && !item.toString().isBlank()) {
                    scopes.add(item.toString());
                }
            }
        }
        return scopes;
    }
}
