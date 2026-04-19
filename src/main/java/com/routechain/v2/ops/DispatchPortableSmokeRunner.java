package com.routechain.v2.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routechain.config.DispatchV2SmokeRunnerProperties;
import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2CompatibleCore;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.HotStartState;
import com.routechain.v2.executor.DispatchAssignment;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "routechain.dispatch-v2.smoke-runner", name = "enabled", havingValue = "true")
public final class DispatchPortableSmokeRunner implements ApplicationRunner {
    static final List<String> EXPECTED_DECISION_STAGES = List.of(
            "eta/context",
            "order-buffer",
            "pair-graph",
            "micro-cluster",
            "boundary-expansion",
            "bundle-pool",
            "pickup-anchor",
            "driver-shortlist/rerank",
            "route-proposal-pool",
            "scenario-evaluation",
            "global-selector",
            "dispatch-executor");

    private final DispatchV2SmokeRunnerProperties smokeRunnerProperties;
    private final RouteChainDispatchV2Properties dispatchProperties;
    private final DispatchV2CompatibleCore dispatchV2CompatibleCore;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext applicationContext;

    public DispatchPortableSmokeRunner(DispatchV2SmokeRunnerProperties smokeRunnerProperties,
                                       RouteChainDispatchV2Properties dispatchProperties,
                                       DispatchV2CompatibleCore dispatchV2CompatibleCore,
                                       ObjectMapper objectMapper,
                                       ConfigurableApplicationContext applicationContext) {
        this.smokeRunnerProperties = smokeRunnerProperties;
        this.dispatchProperties = dispatchProperties;
        this.dispatchV2CompatibleCore = dispatchV2CompatibleCore;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 1;
        try {
            exitCode = execute();
        } finally {
            final int finalExitCode = exitCode;
            SpringApplication.exit(applicationContext, () -> finalExitCode);
            System.exit(finalExitCode);
        }
    }

    private int execute() {
        Instant startedAt = Instant.now();
        Path bundleRoot = resolveAbsolute(Path.of(smokeRunnerProperties.getBundleRoot()));
        Path outputDir = resolveAbsolute(Path.of(smokeRunnerProperties.getOutputDir()));
        Path feedbackBaseDir = bundleRoot.resolve(dispatchProperties.getFeedback().getBaseDir()).normalize();
        Path outputFile = outputDir.resolve(fileNameFor(smokeRunnerProperties.getTraceId()));
        try {
            Files.createDirectories(outputDir);
            DispatchV2Request request = canonicalRequest(smokeRunnerProperties.getTraceId());
            DispatchV2Result result = dispatchV2CompatibleCore.dispatch(request);
            AssignmentConflictSummary conflictSummary = assignmentConflictSummary(result.assignments());
            List<String> failureReasons = validate(result, conflictSummary);
            PortableSmokeArtifact artifact = new PortableSmokeArtifact(
                    "dispatch-v2-portable-smoke/v1",
                    Instant.now(),
                    smokeRunnerProperties.getTraceId(),
                    bundleRoot.toString(),
                    feedbackBaseDir.toString(),
                    smokeRunnerProperties.isExpectHotStart(),
                    blankToNull(smokeRunnerProperties.getExpectedPreviousTraceId()),
                    failureReasons.isEmpty(),
                    failureReasons,
                    result.decisionStages(),
                    result.fallbackUsed(),
                    result.dispatchExecutionSummary(),
                    result.hotStartState(),
                    conflictSummary,
                    fileEvidence(feedbackBaseDir.resolve("decision-log"), startedAt),
                    fileEvidence(feedbackBaseDir.resolve("snapshots"), startedAt),
                    fileEvidence(feedbackBaseDir.resolve("replay"), startedAt));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), artifact);
            return artifact.pass() ? 0 : 1;
        } catch (Exception exception) {
            writeFailureArtifact(outputFile, bundleRoot, feedbackBaseDir, exception);
            return 1;
        }
    }

    static DispatchV2Request canonicalRequest(String traceId) {
        Instant decisionTime = Instant.parse("2026-04-16T12:00:00Z");
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                traceId,
                List.of(
                        canonicalOrder("order-1", 10.7750, 106.7000, 10.7800, 106.7100, decisionTime, false),
                        canonicalOrder("order-2", 10.7760, 106.7010, 10.7810, 106.7120, decisionTime.plusSeconds(120), false),
                        canonicalOrder("order-3", 10.8200, 106.7600, 10.8300, 106.7700, decisionTime.plusSeconds(300), true)),
                List.of(
                        new Driver("driver-1", new GeoPoint(10.7700, 106.6950)),
                        new Driver("driver-2", new GeoPoint(10.7720, 106.6970)),
                        new Driver("driver-3", new GeoPoint(10.7810, 106.7060))),
                List.of(),
                WeatherProfile.CLEAR,
                decisionTime);
    }

    static AssignmentConflictSummary assignmentConflictSummary(List<DispatchAssignment> assignments) {
        Set<String> seenDrivers = new LinkedHashSet<>();
        Set<String> seenOrders = new LinkedHashSet<>();
        List<String> reasons = new ArrayList<>();
        for (DispatchAssignment assignment : assignments) {
            if (!seenDrivers.add(assignment.driverId())) {
                reasons.add("duplicate-driver:" + assignment.driverId());
            }
            for (String orderId : assignment.orderIds()) {
                if (!seenOrders.add(orderId)) {
                    reasons.add("duplicate-order:" + orderId);
                }
            }
        }
        return new AssignmentConflictSummary(reasons.isEmpty(), reasons, List.copyOf(seenDrivers), List.copyOf(seenOrders));
    }

    private static Order canonicalOrder(String orderId,
                                        double pickupLat,
                                        double pickupLon,
                                        double dropLat,
                                        double dropLon,
                                        Instant readyAt,
                                        boolean urgent) {
        return new Order(
                orderId,
                new GeoPoint(pickupLat, pickupLon),
                new GeoPoint(dropLat, dropLon),
                readyAt.minusSeconds(300),
                readyAt,
                20,
                urgent);
    }

    private List<String> validate(DispatchV2Result result, AssignmentConflictSummary conflictSummary) {
        List<String> failureReasons = new ArrayList<>();
        if (!EXPECTED_DECISION_STAGES.equals(result.decisionStages())) {
            failureReasons.add("decision-stage-order-mismatch");
        }
        if (result.fallbackUsed()) {
            failureReasons.add("fallback-used");
        }
        if (result.dispatchExecutionSummary().executedAssignmentCount() <= 0) {
            failureReasons.add("no-executed-assignments");
        }
        if (!conflictSummary.conflictFree()) {
            failureReasons.addAll(conflictSummary.conflictReasons());
        }
        if (smokeRunnerProperties.isExpectHotStart()) {
            HotStartState hotStartState = result.hotStartState();
            if (hotStartState == null || !hotStartState.reuseEligible()) {
                failureReasons.add("hot-start-not-eligible");
            } else {
                if (!hotStartState.pairClusterReused() && !hotStartState.bundlePoolReused() && !hotStartState.routeProposalPoolReused()) {
                    failureReasons.add("hot-start-no-reuse-flags");
                }
                if (hotStartState.reusedStageNames() == null || hotStartState.reusedStageNames().isEmpty()) {
                    failureReasons.add("hot-start-no-reused-stages");
                }
                String expectedPreviousTraceId = blankToNull(smokeRunnerProperties.getExpectedPreviousTraceId());
                if (expectedPreviousTraceId != null && !expectedPreviousTraceId.equals(hotStartState.previousTraceId())) {
                    failureReasons.add("hot-start-previous-trace-mismatch");
                }
            }
        }
        return List.copyOf(failureReasons);
    }

    private static List<FileEvidence> fileEvidence(Path directory, Instant startedAt) {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var stream = Files.walk(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().compareTo(startedAt) >= 0;
                        } catch (Exception exception) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> {
                        try {
                            return new FileEvidence(path.toString(), Files.getLastModifiedTime(path).toInstant(), Files.size(path));
                        } catch (Exception exception) {
                            return new FileEvidence(path.toString(), Instant.EPOCH, -1L);
                        }
                    })
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void writeFailureArtifact(Path outputFile, Path bundleRoot, Path feedbackBaseDir, Exception exception) {
        try {
            Files.createDirectories(outputFile.getParent());
            PortableSmokeArtifact failureArtifact = new PortableSmokeArtifact(
                    "dispatch-v2-portable-smoke/v1",
                    Instant.now(),
                    smokeRunnerProperties.getTraceId(),
                    bundleRoot.toString(),
                    feedbackBaseDir.toString(),
                    smokeRunnerProperties.isExpectHotStart(),
                    blankToNull(smokeRunnerProperties.getExpectedPreviousTraceId()),
                    false,
                    List.of("runner-exception"),
                    List.of(),
                    true,
                    null,
                    HotStartState.empty(),
                    new AssignmentConflictSummary(false, List.of("runner-exception"), List.of(), List.of()),
                    List.of(),
                    List.of(),
                    List.of(),
                    stackTrace(exception));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), failureArtifact);
        } catch (Exception ignored) {
        }
    }

    private static String stackTrace(Exception exception) {
        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String fileNameFor(String traceId) {
        return "dispatch-smoke-" + sanitize(traceId) + ".json";
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Path resolveAbsolute(Path path) {
        return path.isAbsolute() ? path.normalize() : path.toAbsolutePath().normalize();
    }

    record PortableSmokeArtifact(
            String schemaVersion,
            Instant generatedAt,
            String traceId,
            String bundleRoot,
            String feedbackBaseDir,
            boolean expectHotStart,
            String expectedPreviousTraceId,
            boolean pass,
            List<String> failureReasons,
            List<String> decisionStages,
            boolean fallbackUsed,
            com.routechain.v2.executor.DispatchExecutionSummary dispatchExecutionSummary,
            HotStartState hotStartState,
            AssignmentConflictSummary assignmentConflictSummary,
            List<FileEvidence> decisionLogFiles,
            List<FileEvidence> snapshotFiles,
            List<FileEvidence> replayFiles,
            String exceptionStackTrace) {
        PortableSmokeArtifact(String schemaVersion,
                              Instant generatedAt,
                              String traceId,
                              String bundleRoot,
                              String feedbackBaseDir,
                              boolean expectHotStart,
                              String expectedPreviousTraceId,
                              boolean pass,
                              List<String> failureReasons,
                              List<String> decisionStages,
                              boolean fallbackUsed,
                              com.routechain.v2.executor.DispatchExecutionSummary dispatchExecutionSummary,
                              HotStartState hotStartState,
                              AssignmentConflictSummary assignmentConflictSummary,
                              List<FileEvidence> decisionLogFiles,
                              List<FileEvidence> snapshotFiles,
                              List<FileEvidence> replayFiles) {
            this(schemaVersion, generatedAt, traceId, bundleRoot, feedbackBaseDir, expectHotStart, expectedPreviousTraceId, pass, failureReasons, decisionStages, fallbackUsed, dispatchExecutionSummary, hotStartState, assignmentConflictSummary, decisionLogFiles, snapshotFiles, replayFiles, null);
        }
    }

    record AssignmentConflictSummary(
            boolean conflictFree,
            List<String> conflictReasons,
            List<String> driverIds,
            List<String> orderIds) {
    }

    record FileEvidence(
            String path,
            Instant lastModifiedAt,
            long sizeBytes) {
    }
}
