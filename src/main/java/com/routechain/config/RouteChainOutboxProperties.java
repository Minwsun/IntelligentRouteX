package com.routechain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routechain.outbox")
public class RouteChainOutboxProperties {
    private boolean enabled;
    private String bootstrapServers = "localhost:9092";
    private String clientId = "routechain-outbox-relay";
    private String topicPrefix = "";
    private int batchSize = 25;
    private long pollIntervalMs = 1_000L;
    private long staleClaimTtlMs = 30_000L;
    private long retryBackoffMs = 2_000L;
    private long publishTimeoutMs = 5_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getStaleClaimTtlMs() {
        return staleClaimTtlMs;
    }

    public void setStaleClaimTtlMs(long staleClaimTtlMs) {
        this.staleClaimTtlMs = staleClaimTtlMs;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public long getPublishTimeoutMs() {
        return publishTimeoutMs;
    }

    public void setPublishTimeoutMs(long publishTimeoutMs) {
        this.publishTimeoutMs = publishTimeoutMs;
    }
}
