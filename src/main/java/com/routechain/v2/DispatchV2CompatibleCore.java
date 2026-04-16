package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.core.AdaptiveWeightEngine;
import com.routechain.core.CompactDispatchCore;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactPolicyConfig;
import com.routechain.core.WeightSnapshot;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.v2.feedback.DispatchV2EvidenceMapper;
import com.routechain.v2.feedback.DispatchV2LearningState;
import com.routechain.v2.feedback.DispatchV2ReplayTrainer;

import java.time.Instant;
import java.util.List;

public final class DispatchV2CompatibleCore {
    private final RouteChainDispatchV2Properties properties;
    private final CompactDispatchCore legacyCompactCore;
    private final DispatchV2Core dispatchV2Core;
    private final AdaptiveWeightEngine compatibilityWeightEngine;
    private final DispatchV2ReplayTrainer replayTrainer;
    private final DispatchV2EvidenceMapper evidenceMapper;
    private final DispatchV2LearningState learningState;

    public DispatchV2CompatibleCore(CompactPolicyConfig policyConfig, RouteChainDispatchV2Properties properties) {
        CompactPolicyConfig safePolicyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
        this.properties = properties == null ? RouteChainDispatchV2Properties.defaults() : properties;
        this.legacyCompactCore = new CompactDispatchCore(safePolicyConfig);
        this.dispatchV2Core = new DispatchV2Core(this.properties);
        this.compatibilityWeightEngine = new AdaptiveWeightEngine(safePolicyConfig);
        this.replayTrainer = new DispatchV2ReplayTrainer();
        this.evidenceMapper = new DispatchV2EvidenceMapper();
        this.learningState = new DispatchV2LearningState();
    }

    public CompactDispatchDecision dispatch(List<Order> openOrders,
                                            List<Driver> availableDrivers,
                                            List<Region> regions,
                                            int simulatedHour,
                                            double trafficIntensity,
                                            WeatherProfile weatherProfile,
                                            Instant decisionTime) {
        if (!properties.isEnabled()) {
            return legacyCompactCore.dispatch(
                    openOrders,
                    availableDrivers,
                    regions,
                    simulatedHour,
                    trafficIntensity,
                    weatherProfile,
                    decisionTime);
        }

        DispatchV2Request request = new DispatchV2Request(
                List.copyOf(openOrders),
                List.copyOf(availableDrivers),
                List.copyOf(regions),
                simulatedHour,
                trafficIntensity,
                weatherProfile,
                decisionTime,
                "dispatch-v2");

        try {
            DispatchV2Result result = dispatchV2Core.dispatch(request);
            if (result.selectedRoutes().isEmpty() && properties.isDeterministicLegacyFallbackEnabled()) {
                return legacyCompactCore.dispatch(
                        openOrders,
                        availableDrivers,
                        regions,
                        simulatedHour,
                        trafficIntensity,
                        weatherProfile,
                        decisionTime);
            }
            replayTrainer.onDispatchCompleted(result, compatibilityWeightEngine);
            return evidenceMapper.toCompactDecision(request, result, compatibilityWeightEngine);
        } catch (RuntimeException failure) {
            if (!properties.isDeterministicLegacyFallbackEnabled()) {
                throw failure;
            }
            return legacyCompactCore.dispatch(
                    openOrders,
                    availableDrivers,
                    regions,
                    simulatedHour,
                    trafficIntensity,
                    weatherProfile,
                    decisionTime);
        }
    }

    public AdaptiveWeightEngine adaptiveWeightEngine() {
        return properties.isEnabled() ? compatibilityWeightEngine : legacyCompactCore.adaptiveWeightEngine();
    }

    public WeightSnapshot currentWeightSnapshot() {
        return adaptiveWeightEngine().snapshot();
    }

    public boolean isLearningFrozen() {
        return adaptiveWeightEngine().isLearningFrozen();
    }

    public String latestSnapshotTag() {
        return learningState.latestSnapshotTag();
    }

    public boolean rollbackAvailable() {
        return learningState.rollbackAvailable();
    }

    public void syncLearningState(String latestSnapshotTag, boolean rollbackAvailable) {
        learningState.update(latestSnapshotTag, rollbackAvailable);
    }

    public boolean isDispatchV2Enabled() {
        return properties.isEnabled();
    }
}
