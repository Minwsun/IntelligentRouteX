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
import com.routechain.ai.ModelBundleManifest;
import com.routechain.graph.GraphAffinityScorer;
import com.routechain.graph.GraphShadowProjector;
import com.routechain.infra.Events.*;
import com.routechain.simulation.ReplayCompareResult;
import com.routechain.simulation.RunReport;
import com.routechain.simulation.DispatchOptimizer;
import com.routechain.simulation.TimefoldOnlineOptimizer;

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
    private static final GraphShadowProjector GRAPH_SHADOW_PROJECTOR = new GraphShadowProjector(FEATURE_STORE);
    private static final GraphAffinityScorer GRAPH_AFFINITY_SCORER = new GraphAffinityScorer();
    private static final DispatchOptimizer DISPATCH_OPTIMIZER = new TimefoldOnlineOptimizer();
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
        registerDefaultModelBundles();
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

    public static GraphShadowProjector getGraphShadowProjector() {
        return GRAPH_SHADOW_PROJECTOR;
    }

    public static GraphAffinityScorer getGraphAffinityScorer() {
        return GRAPH_AFFINITY_SCORER;
    }

    public static DispatchOptimizer getDispatchOptimizer() {
        return DISPATCH_OPTIMIZER;
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

    private static void registerDefaultModelBundles() {
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "eta-model",
                "eta-model-xgb-v1",
                "eta-features-v2",
                "models/onnx/eta-model-xgb-v1.onnx",
                70,
                "online-linear-fallback",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "plan-ranker-model",
                "dispatch-ranker-lambdamart-v1",
                "dispatch-ranker-features-v2",
                "models/onnx/dispatch-ranker-lambdamart-v1.onnx",
                90,
                "heuristic-plan-ranker-fallback",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "empty-zone-risk-model",
                "empty-zone-logit-v1",
                "empty-zone-features-v1",
                "models/onnx/empty-zone-logit-v1.onnx",
                60,
                "rule-based-empty-risk-fallback",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "batch-value-model",
                "batch-value-logit-v1",
                "batch-value-features-v1",
                "models/onnx/batch-value-logit-v1.onnx",
                60,
                "online-batch-fallback",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "stress-rescue-model",
                "stress-rescue-logit-v1",
                "stress-rescue-features-v1",
                "models/onnx/stress-rescue-logit-v1.onnx",
                65,
                "rule-based-stress-fallback",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "driver-positioning-model",
                "driver-positioning-logit-v1",
                "driver-positioning-features-v1",
                "models/onnx/driver-positioning-logit-v1.onnx",
                65,
                "graph-attraction-fallback",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "neural-route-prior-model",
                "routefinder-v1",
                "neural-route-prior-features-v1",
                "models/routefinder/checkpoints/50/rf-transformer.ckpt",
                30000,
                "deterministic-no-prior",
                true
        ));
        MODEL_ARTIFACT_PROVIDER.registerBundle(new ModelBundleManifest(
                "neural-route-prior-model",
                "rrnco-v1",
                "neural-route-prior-features-v1",
                "models/rrnco/checkpoints/rcvrptw/epoch_199.ckpt",
                30000,
                "deterministic-no-prior",
                false
        ));
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
            case OfferBatchCreated ignored -> "dispatch.offer_batch";
            case DriverOfferCreated ignored -> "dispatch.offer_created";
            case DriverOfferAccepted ignored -> "dispatch.offer_accepted";
            case DriverOfferDeclined ignored -> "dispatch.offer_declined";
            case DriverOfferExpired ignored -> "dispatch.offer_expired";
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
