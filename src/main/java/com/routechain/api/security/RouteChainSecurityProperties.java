package com.routechain.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routechain.security")
public class RouteChainSecurityProperties {
    private boolean enabled;
    private String actorIdClaim = "sub";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActorIdClaim() {
        return actorIdClaim;
    }

    public void setActorIdClaim(String actorIdClaim) {
        this.actorIdClaim = actorIdClaim;
    }
}
