package com.routechain.infra;

import com.routechain.ai.DispatchBrainAgent;
import com.routechain.ai.DefaultLLMEscalationGate;
import com.routechain.ai.DefaultModelArtifactProvider;
import com.routechain.ai.GroqLLMAdvisorClient;
import com.routechain.ai.GroqRuntimeConfig;
import com.routechain.ai.LLMAdvisorClient;
import com.routechain.ai.LLMEscalationGate;
import com.routechain.ai.ModelArtifactProvider;
import com.routechain.ai.OfflineFallbackLLMAdvisorClient;
import com.routechain.infra.Events.*;
import com.routechain.simulation.ReplayCompareResult;
import com.routechain.simulation.RunReport;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central bootstrap for the local-first RouteChain Apex platform runtime.
 */
public final class PlatformRuntimeBootstrap {
    private static final Path ROOT = Path.of("build", "routechain-apex");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final LocalAdminQueryService ADMIN_QUERY_SERVICE = new LocalAdminQueryService();
    private static final InMemoryFeatureStore FEATURE_STORE = new InMemoryFeatureStore();
    private static final JsonlDispatchFactSink DISPATCH_FACT_SINK =
            new JsonlDispatchFactSink(ROOT.resolve("facts"));
    private static final JsonlCanonicalEventPublisher CANONICAL_EVENT_PUBLISHER =
            new JsonlCanonicalEventPublisher(ROOT.resolve("event-tape"));
    private static final DefaultModelArtifactProvider MODEL_ARTIFACT_PROVIDER =
            new DefaultModelArtifactProvider();
    private static final OfflineFallbackLLMAdvisorClient OFFLINE_LLM_ADVISOR_CLIENT =
            new OfflineFallbackLLMAdvisorClient();
    private static final DefaultLLMEscalationGate DEFAULT_LLM_ESCALATION_GATE =
            new DefaultLLMEscalationGate();
    private static volatile LLMAdvisorClient llmAdvisorClient = OFFLINE_LLM_ADVISOR_CLIENT;
    private static volatile LLMEscalationGate llmEscalationGate = DEFAULT_LLM_ESCALATION_GATE;

    private PlatformRuntimeBootstrap() {}

    static {
        refreshLlmRuntime();
    }

    public static void ensureInitialized(EventBus eventBus) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        ADMIN_QUERY_SERVICE.updateLlmMode(llmAdvisorClient.mode());
        eventBus.subscribeAll(event -> {
            String topic = topicFor(event);
            ADMIN_QUERY_SERVICE.onCanonicalEvent(topic);
            if (shouldPersistToEventTape(event)) {
                CANONICAL_EVENT_PUBLISHER.publish(topic, event);
            }
            if (event instanceof AlertRaised alert) {
                ADMIN_QUERY_SERVICE.onAlert(
                        alert.id(), alert.title(), alert.regionId(), alert.timestamp());
            }
        });
    }

    public static FeatureStore getFeatureStore() {
        return FEATURE_STORE;
    }

    public static DispatchFactSink getDispatchFactSink() {
        return DISPATCH_FACT_SINK;
    }

    public static CanonicalEventPublisher getCanonicalEventPublisher() {
        return CANONICAL_EVENT_PUBLISHER;
    }

    public static ModelArtifactProvider getModelArtifactProvider() {
        return MODEL_ARTIFACT_PROVIDER;
    }

    public static AdminQueryService getAdminQueryService() {
        return ADMIN_QUERY_SERVICE;
    }

    public static LLMAdvisorClient getLlmAdvisorClient() {
        return llmAdvisorClient;
    }

    public static LLMEscalationGate getLlmEscalationGate() {
        return llmEscalationGate;
    }

    public static synchronized void refreshLlmRuntime() {
        GroqRuntimeConfig groqConfig = GroqRuntimeConfig.fromEnvironment();
        if (groqConfig.canUseGroq()) {
            llmAdvisorClient = new GroqLLMAdvisorClient(groqConfig, ADMIN_QUERY_SERVICE::updateLlmRuntime);
            ADMIN_QUERY_SERVICE.updateLlmMode(llmAdvisorClient.mode());
        } else {
            llmAdvisorClient = OFFLINE_LLM_ADVISOR_CLIENT;
            ADMIN_QUERY_SERVICE.updateLlmMode(llmAdvisorClient.mode());
            ADMIN_QUERY_SERVICE.updateLlmRuntime(AdminQueryService.LlmRuntimeStatus.offlineDefault(
                    llmAdvisorClient.mode()));
        }
        llmEscalationGate = DEFAULT_LLM_ESCALATION_GATE;
    }

    static synchronized void overrideLlmRuntimeForTesting(LLMAdvisorClient client,
                                                          LLMEscalationGate gate,
                                                          AdminQueryService.LlmRuntimeStatus runtimeStatus) {
        llmAdvisorClient = client == null ? OFFLINE_LLM_ADVISOR_CLIENT : client;
        llmEscalationGate = gate == null ? DEFAULT_LLM_ESCALATION_GATE : gate;
        ADMIN_QUERY_SERVICE.updateLlmMode(llmAdvisorClient.mode());
        ADMIN_QUERY_SERVICE.updateLlmRuntime(runtimeStatus == null
                ? AdminQueryService.LlmRuntimeStatus.offlineDefault(llmAdvisorClient.mode())
                : runtimeStatus);
    }

    public static void registerDispatchBrain(DispatchBrainAgent brain) {
        ADMIN_QUERY_SERVICE.registerDispatchBrain(brain.agentId(), brain.describeTools());
    }

    public static void updateExecutionProfile(String executionProfile) {
        ADMIN_QUERY_SERVICE.updateExecutionProfile(executionProfile);
    }

    public static void recordRunReport(RunReport report) {
        DISPATCH_FACT_SINK.recordRunReport(report);
        ADMIN_QUERY_SERVICE.onRunReport(report);
    }

    public static void recordReplayCompare(ReplayCompareResult compare) {
        DISPATCH_FACT_SINK.recordReplayCompare(compare);
        ADMIN_QUERY_SERVICE.onReplayCompare(compare);
        CANONICAL_EVENT_PUBLISHER.publish("benchmark.compare", compare);
    }

    private static String topicFor(Object event) {
        return switch (event) {
            case SimulationStarted ignored -> "simulation.started";
            case SimulationStopped ignored -> "simulation.stopped";
            case SimulationTick ignored -> "simulation.tick";
            case SimulationReset ignored -> "simulation.reset";
            case OrderCreated ignored -> "orders.created";
            case OrderAssigned ignored -> "orders.assigned";
            case OrderPickedUp ignored -> "orders.picked_up";
            case OrderDelivered ignored -> "orders.delivered";
            case OrderCancelled ignored -> "orders.cancelled";
            case OrderFailed ignored -> "orders.failed";
            case DriverLocationUpdated ignored -> "drivers.telemetry";
            case DriverStateChanged ignored -> "drivers.state";
            case DriverOnline ignored -> "drivers.online";
            case DriverOffline ignored -> "drivers.offline";
            case TrafficUpdated ignored -> "traffic.region";
            case TrafficSegmentUpdated ignored -> "traffic.corridor";
            case WeatherChanged ignored -> "weather.region";
            case SurgeDetected ignored -> "alerts.surge";
            case DriverShortageDetected ignored -> "alerts.shortage";
            case AlertRaised ignored -> "alerts.raised";
            case DispatchDecision ignored -> "dispatch.selected";
            case ReDispatchTriggered ignored -> "dispatch.redispatch";
            case AiInsight ignored -> "ai.insight";
            case TimelineSnapshot ignored -> "simulation.timeline";
            case MetricsSnapshot ignored -> "simulation.metrics";
            case RunReportGenerated ignored -> "benchmark.run_report";
            case ReplayCompareCompleted ignored -> "benchmark.replay_compare";
            default -> "platform.misc";
        };
    }

    private static boolean shouldPersistToEventTape(Object event) {
        return switch (event) {
            case SimulationTick ignored -> false;
            case DriverLocationUpdated ignored -> false;
            case TrafficSegmentUpdated ignored -> false;
            case TimelineSnapshot ignored -> false;
            case MetricsSnapshot ignored -> false;
            default -> true;
        };
    }
}
