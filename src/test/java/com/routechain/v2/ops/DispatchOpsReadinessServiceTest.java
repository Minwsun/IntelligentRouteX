package com.routechain.v2.ops;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.feedback.DispatchRuntimeSnapshot;
import com.routechain.v2.feedback.InMemorySnapshotStore;
import com.routechain.v2.feedback.SnapshotBuilder;
import com.routechain.v2.feedback.SnapshotService;
import com.routechain.v2.feedback.WarmStartManager;
import com.routechain.v2.integration.ForecastClient;
import com.routechain.v2.integration.ForecastResult;
import com.routechain.v2.integration.GreedRlBundleFeatureVector;
import com.routechain.v2.integration.GreedRlBundleResult;
import com.routechain.v2.integration.GreedRlClient;
import com.routechain.v2.integration.GreedRlSequenceFeatureVector;
import com.routechain.v2.integration.GreedRlSequenceResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.PostDropShiftFeatureVector;
import com.routechain.v2.integration.RouteFinderClient;
import com.routechain.v2.integration.RouteFinderFeatureVector;
import com.routechain.v2.integration.RouteFinderResult;
import com.routechain.v2.integration.TabularScoreResult;
import com.routechain.v2.integration.TabularScoringClient;
import com.routechain.v2.integration.WorkerReadyState;
import com.routechain.v2.cluster.PairFeatureVector;
import com.routechain.v2.context.EtaFeatureVector;
import com.routechain.v2.route.DriverFitFeatureVector;
import com.routechain.v2.route.RouteValueFeatureVector;
import com.routechain.v2.integration.DemandShiftFeatureVector;
import com.routechain.v2.integration.ZoneBurstFeatureVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchOpsReadinessServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotCarriesWarmBootWorkerReadinessAndTomTomMissingKey() throws Exception {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        properties.getMl().getRoutefinder().setEnabled(true);
        properties.getMl().getGreedrl().setEnabled(true);
        properties.getMl().getForecast().setEnabled(true);
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);
        properties.getTraffic().setApiKey("");
        properties.getFeedback().setBaseDir("build/dispatch-v2-feedback");

        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        snapshotStore.save(new DispatchRuntimeSnapshot(
                "dispatch-runtime-snapshot/v1",
                "snapshot-1",
                "trace-warm",
                Instant.parse("2026-04-18T00:00:00Z"),
                List.of("eta/context"),
                List.of("proposal-1"),
                List.of("assignment-1"),
                List.of("cluster-1"),
                List.of("bundle-1"),
                List.of("route-1"),
                1.0,
                List.of()));
        WarmStartManager warmStartManager = new WarmStartManager(
                properties,
                new SnapshotService(properties, new SnapshotBuilder(), snapshotStore));

        Path manifestPath = tempDir.resolve("model-manifest.yaml");
        Files.writeString(manifestPath, """
                schemaVersion: model-manifest/v2
                workers:
                  - worker_name: ml-tabular-worker
                    model_name: tabular-linear
                    model_version: v1
                    artifact_digest: sha256:tabular
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                    loaded_model_fingerprint: sha256:tabular-fingerprint
                  - worker_name: ml-routefinder-worker
                    model_name: routefinder-local
                    model_version: v2
                    artifact_digest: sha256:routefinder
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                    loaded_model_fingerprint: sha256:routefinder-fingerprint
                  - worker_name: ml-greedrl-worker
                    model_name: greedrl-local
                    model_version: v3
                    artifact_digest: sha256:greedrl
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                    loaded_model_fingerprint: sha256:greedrl-fingerprint
                  - worker_name: ml-forecast-worker
                    model_name: chronos-2
                    model_version: v4
                    artifact_digest: sha256:chronos
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                    loaded_model_fingerprint: sha256:chronos-fingerprint
                """);

        DispatchOpsReadinessService service = new DispatchOpsReadinessService(
                properties,
                warmStartManager,
                tabularClient(WorkerReadyState.ready(new MlWorkerMetadata("tabular-linear", "v1", "sha256:tabular", 0L))),
                routeFinderClient(WorkerReadyState.notReady("worker-unreachable", new MlWorkerMetadata("routefinder-local", "v2", "sha256:routefinder", 0L))),
                greedRlClient(WorkerReadyState.ready(new MlWorkerMetadata("greedrl-local", "v3", "sha256:greedrl", 0L))),
                forecastClient(WorkerReadyState.ready(new MlWorkerMetadata("chronos-2", "v4", "sha256:chronos", 0L))),
                manifestPath);

        DispatchOpsReadinessSnapshot snapshot = service.snapshot();

        assertEquals("dispatch-ops-readiness-snapshot/v1", snapshot.schemaVersion());
        assertEquals("WARM", snapshot.bootMode());
        assertTrue(snapshot.latestSnapshotLoaded());
        assertEquals("snapshot-1", snapshot.latestSnapshotId());
        assertEquals("trace-warm", snapshot.latestSnapshotTraceId());
        assertEquals(4, snapshot.workers().size());

        DispatchOpsReadinessSnapshot.DispatchOpsWorkerReadiness routeFinder = snapshot.workers().stream()
                .filter(worker -> worker.workerName().equals("ml-routefinder-worker"))
                .findFirst()
                .orElseThrow();
        assertFalse(routeFinder.ready());
        assertEquals("worker-unreachable", routeFinder.reason());
        assertEquals("sha256:routefinder-fingerprint", routeFinder.loadedModelFingerprint());

        DispatchOpsReadinessSnapshot.DispatchOpsLiveSourceStatus tomTom = snapshot.liveSources().stream()
                .filter(source -> source.sourceName().equals("tomtom-traffic"))
                .findFirst()
                .orElseThrow();
        assertTrue(tomTom.enabled());
        assertFalse(tomTom.apiKeyPresent());
        assertEquals("missing-api-key", tomTom.observedMode());
    }

    private TabularScoringClient tabularClient(WorkerReadyState readyState) {
        return new TabularScoringClient() {
            @Override
            public TabularScoreResult scoreEtaResidual(EtaFeatureVector etaFeatureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TabularScoreResult scorePair(PairFeatureVector pairFeatureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TabularScoreResult scoreDriverFit(DriverFitFeatureVector driverFitFeatureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public TabularScoreResult scoreRouteValue(RouteValueFeatureVector routeValueFeatureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkerReadyState readyState() {
                return readyState;
            }
        };
    }

    private RouteFinderClient routeFinderClient(WorkerReadyState readyState) {
        return new RouteFinderClient() {
            @Override
            public RouteFinderResult refineRoute(RouteFinderFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RouteFinderResult generateAlternatives(RouteFinderFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkerReadyState readyState() {
                return readyState;
            }
        };
    }

    private GreedRlClient greedRlClient(WorkerReadyState readyState) {
        return new GreedRlClient() {
            @Override
            public GreedRlBundleResult proposeBundles(GreedRlBundleFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public GreedRlSequenceResult proposeSequence(GreedRlSequenceFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkerReadyState readyState() {
                return readyState;
            }
        };
    }

    private ForecastClient forecastClient(WorkerReadyState readyState) {
        return new ForecastClient() {
            @Override
            public ForecastResult forecastDemandShift(DemandShiftFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ForecastResult forecastZoneBurst(ZoneBurstFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ForecastResult forecastPostDropShift(PostDropShiftFeatureVector featureVector, long timeoutBudgetMs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public WorkerReadyState readyState() {
                return readyState;
            }
        };
    }
}
