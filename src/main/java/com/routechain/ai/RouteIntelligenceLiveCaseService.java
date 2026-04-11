package com.routechain.api.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.routechain.infra.GsonSupport;
import com.routechain.simulation.RouteIntelligenceDemoProofRunner;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RouteIntelligenceLiveCaseService {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private static final Path CONTROL_ROOM_JSON =
            Path.of("build", "routechain-apex", "benchmarks", "control-room", "control_room_latest.json");

    public SelectedLiveCaseBundle buildSelectedLiveCase(String pickupCellId, String driverId, String mode) {
        String normalizedMode = normalizeMode(mode);
        Map<String, Object> cityState = readJsonMap();
        List<Map<String, Object>> cells = listOfMaps(cityState, "cityTwinCells");
        List<Map<String, Object>> driverFutureValues = listOfMaps(cityState, "driverFutureValues");
        List<Map<String, Object>> marketplaceEdges = listOfMaps(cityState, "marketplaceEdges");
        List<Map<String, Object>> riderCopilot = listOfMaps(cityState, "riderCopilot");

        Map<String, Object> selectedCell = selectPickupCell(cells, pickupCellId);
        String selectedCellId = asString(selectedCell.get("cellId"));
        List<Map<String, Object>> selectedDrivers = selectDriverSuggestions(driverFutureValues, selectedCellId, driverId);
        List<Map<String, Object>> selectedEdges = selectMarketplaceEdges(marketplaceEdges, selectedCellId, selectedDrivers, driverId);
        List<Map<String, Object>> selectedCopilot = riderCopilot.stream()
                .filter(entry -> selectedCellId.equalsIgnoreCase(asString(entry.get("targetCellId"))))
                .limit(3)
                .toList();

        String matchedProofCaseId = matchProofCaseId(selectedCell, selectedEdges);
        RouteIntelligenceDemoProofRunner.ProofCaseExecution proofComparison =
                RouteIntelligenceDemoProofRunner.runProofCase(matchedProofCaseId, normalizedMode);

        return new SelectedLiveCaseBundle(
                selectedCellId,
                asString(selectedCell.get("serviceTier")),
                driverId == null || driverId.isBlank() ? "AUTO_NEARBY" : driverId,
                normalizedMode,
                classifyConfidence(selectedDrivers, selectedEdges),
                "LIVE_CITY_STATE_WITH_MATCHED_PROOF_REFERENCE",
                matchedProofCaseId,
                buildSelectionBasis(selectedCell, selectedDrivers, selectedEdges, matchedProofCaseId),
                toPickupCellView(selectedCell),
                selectedDrivers.stream().map(this::toDriverSuggestionView).toList(),
                selectedEdges.stream().map(this::toMarketplaceEdgeView).toList(),
                selectedCopilot,
                proofComparison,
                buildLiveCaseExplanation(selectedCell, selectedDrivers, selectedEdges, proofComparison)
        );
    }

    private Map<String, Object> readJsonMap() {
        try {
            if (Files.notExists(CONTROL_ROOM_JSON)) {
                return Map.of();
            }
            Map<String, Object> parsed = GSON.fromJson(Files.readString(CONTROL_ROOM_JSON, StandardCharsets.UTF_8), MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of("status", "error", "message", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Map<String, Object> root, String key) {
        Object raw = root.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private Map<String, Object> selectPickupCell(List<Map<String, Object>> cells, String pickupCellId) {
        if (cells.isEmpty()) {
            return new LinkedHashMap<>(Map.of(
                    "cellId", pickupCellId == null ? "unknown-cell" : pickupCellId,
                    "serviceTier", "instant",
                    "centerLat", 10.7769,
                    "centerLng", 106.7009,
                    "demandForecast10m", 0.0,
                    "trafficForecast10m", 0.0,
                    "shortageForecast10m", 0.0,
                    "postDropOpportunity10m", 0.0,
                    "emptyZoneRisk10m", 1.0,
                    "compositeValue", 0.0
            ));
        }
        if (pickupCellId != null && !pickupCellId.isBlank()) {
            return cells.stream()
                    .filter(cell -> pickupCellId.equalsIgnoreCase(asString(cell.get("cellId"))))
                    .findFirst()
                    .orElse(cells.get(0));
        }
        return cells.stream()
                .max(Comparator.comparingDouble(cell -> asDouble(cell.get("compositeValue"))))
                .orElse(cells.get(0));
    }

    private List<Map<String, Object>> selectDriverSuggestions(List<Map<String, Object>> driverFutureValues,
                                                              String selectedCellId,
                                                              String requestedDriverId) {
        Comparator<Map<String, Object>> comparator =
                Comparator.comparingDouble((Map<String, Object> entry) -> asDouble(entry.get("futureValueScore")))
                        .reversed();
        if (requestedDriverId != null && !requestedDriverId.isBlank()) {
            List<Map<String, Object>> exact = driverFutureValues.stream()
                    .filter(entry -> requestedDriverId.equalsIgnoreCase(asString(entry.get("driverId"))))
                    .sorted(comparator)
                    .limit(4)
                    .toList();
            if (!exact.isEmpty()) {
                return exact;
            }
        }
        List<Map<String, Object>> targeted = driverFutureValues.stream()
                .filter(entry -> selectedCellId.equalsIgnoreCase(asString(entry.get("targetCellId"))))
                .sorted(comparator)
                .limit(4)
                .toList();
        if (!targeted.isEmpty()) {
            return targeted;
        }
        return driverFutureValues.stream()
                .sorted(comparator)
                .limit(4)
                .toList();
    }

    private List<Map<String, Object>> selectMarketplaceEdges(List<Map<String, Object>> marketplaceEdges,
                                                             String selectedCellId,
                                                             List<Map<String, Object>> selectedDrivers,
                                                             String requestedDriverId) {
        Set<String> driverIds = new HashSet<>();
        for (Map<String, Object> selectedDriver : selectedDrivers) {
            driverIds.add(asString(selectedDriver.get("driverId")));
        }
        if (requestedDriverId != null && !requestedDriverId.isBlank()) {
            driverIds.add(requestedDriverId);
        }
        Comparator<Map<String, Object>> comparator =
                Comparator.comparingDouble((Map<String, Object> entry) -> asDouble(entry.get("edgeScore")))
                        .reversed();
        List<Map<String, Object>> directMatches = marketplaceEdges.stream()
                .filter(entry -> selectedCellId.equalsIgnoreCase(asString(entry.get("pickupCellId")))
                        || selectedCellId.equalsIgnoreCase(asString(entry.get("dropoffCellId"))))
                .filter(entry -> driverIds.isEmpty() || driverIds.contains(asString(entry.get("driverId"))))
                .sorted(comparator)
                .limit(6)
                .toList();
        if (!directMatches.isEmpty()) {
            return directMatches;
        }
        List<Map<String, Object>> driverMatches = marketplaceEdges.stream()
                .filter(entry -> driverIds.contains(asString(entry.get("driverId"))))
                .sorted(comparator)
                .limit(6)
                .toList();
        if (!driverMatches.isEmpty()) {
            return driverMatches;
        }
        return marketplaceEdges.stream()
                .sorted(comparator)
                .limit(6)
                .toList();
    }

    private String matchProofCaseId(Map<String, Object> selectedCell, List<Map<String, Object>> selectedEdges) {
        double demand = asDouble(selectedCell.get("demandForecast10m"));
        double traffic = asDouble(selectedCell.get("trafficForecast10m"));
        double shortage = asDouble(selectedCell.get("shortageForecast10m"));
        double opportunity = asDouble(selectedCell.get("postDropOpportunity10m"));
        boolean anyBorrowed = selectedEdges.stream().anyMatch(edge -> asBoolean(edge.get("borrowed")));
        if (shortage >= 0.95 || anyBorrowed) {
            return "shortage-controlled-borrow";
        }
        if (traffic >= 0.66 && demand >= 8.0) {
            return "dinner-peak-hcmc-landing-win";
        }
        if (traffic >= 0.60) {
            return "rush-hour-reject-bad-batch";
        }
        if (opportunity >= 0.86) {
            return "dinner-peak-hcmc-landing-win";
        }
        return "clear-smart-batch-win";
    }

    private String classifyConfidence(List<Map<String, Object>> selectedDrivers,
                                      List<Map<String, Object>> selectedEdges) {
        if (!selectedDrivers.isEmpty() && !selectedEdges.isEmpty()) {
            return "HIGH";
        }
        if (!selectedDrivers.isEmpty() || !selectedEdges.isEmpty()) {
            return "MEDIUM";
        }
        return "LOW_CONFIDENCE_LIVE_CASE";
    }

    private String buildSelectionBasis(Map<String, Object> selectedCell,
                                       List<Map<String, Object>> selectedDrivers,
                                       List<Map<String, Object>> selectedEdges,
                                       String matchedProofCaseId) {
        return "pickupCell=" + asString(selectedCell.get("cellId"))
                + " demand10=" + formatDecimal(asDouble(selectedCell.get("demandForecast10m")))
                + " traffic10=" + formatDecimal(asDouble(selectedCell.get("trafficForecast10m")))
                + " postDrop=" + formatDecimal(asDouble(selectedCell.get("postDropOpportunity10m")))
                + " drivers=" + selectedDrivers.size()
                + " edges=" + selectedEdges.size()
                + " matchedProof=" + matchedProofCaseId;
    }

    private String buildLiveCaseExplanation(Map<String, Object> selectedCell,
                                            List<Map<String, Object>> selectedDrivers,
                                            List<Map<String, Object>> selectedEdges,
                                            RouteIntelligenceDemoProofRunner.ProofCaseExecution proofComparison) {
        String cellId = asString(selectedCell.get("cellId"));
        String postDrop = formatDecimal(asDouble(selectedCell.get("postDropOpportunity10m")));
        String emptyRisk = formatDecimal(asDouble(selectedCell.get("emptyZoneRisk10m")));
        String leadDriver = selectedDrivers.isEmpty() ? "auto-driver" : asString(selectedDrivers.get(0).get("driverId"));
        String edgeReason = selectedEdges.isEmpty()
                ? "no direct marketplace edge was available, so the system falls back to zone-level opportunity evidence"
                : asString(selectedEdges.get(0).get("rationale"));
        return "Selected live case around pickup cell " + cellId
                + " uses driver scope " + leadDriver
                + ". Zone post-drop opportunity is " + postDrop
                + " with empty-risk " + emptyRisk
                + ". Top live edge says: " + edgeReason
                + ". Policy compare is anchored to curated proof case "
                + proofComparison.caseId()
                + " so the live city-state can still be defended with stable multi-policy evidence.";
    }

    private PickupCellView toPickupCellView(Map<String, Object> cell) {
        return new PickupCellView(
                asString(cell.get("cellId")),
                asString(cell.get("serviceTier")),
                asDouble(cell.get("centerLat")),
                asDouble(cell.get("centerLng")),
                asDouble(cell.get("demandForecast10m")),
                asDouble(cell.get("trafficForecast10m")),
                asDouble(cell.get("shortageForecast10m")),
                asDouble(cell.get("postDropOpportunity10m")),
                asDouble(cell.get("emptyZoneRisk10m")),
                asDouble(cell.get("compositeValue"))
        );
    }

    private DriverSuggestionView toDriverSuggestionView(Map<String, Object> entry) {
        return new DriverSuggestionView(
                asString(entry.get("driverId")),
                asString(entry.get("currentCellId")),
                asString(entry.get("targetCellId")),
                asDouble(entry.get("futureValueScore")),
                asDouble(entry.get("postDropOpportunity")),
                asDouble(entry.get("emptyZoneRisk")),
                asString(entry.get("recommendation"))
        );
    }

    private MarketplaceEdgeView toMarketplaceEdgeView(Map<String, Object> entry) {
        return new MarketplaceEdgeView(
                asString(entry.get("edgeId")),
                asString(entry.get("driverId")),
                asString(entry.get("orderId")),
                asString(entry.get("pickupCellId")),
                asString(entry.get("dropoffCellId")),
                asDouble(entry.get("pickupEtaMinutes")),
                asDouble(entry.get("deadheadKm")),
                asDouble(entry.get("edgeScore")),
                asDouble(entry.get("continuationScore")),
                asBoolean(entry.get("borrowed")),
                asString(entry.get("rationale"))
        );
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "shadow";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return "live".equals(normalized) ? "live" : "shadow";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public record SelectedLiveCaseBundle(
            String pickupCellId,
            String serviceTier,
            String driverScope,
            String mode,
            String confidence,
            String sourceMode,
            String matchedProofCaseId,
            String selectionBasis,
            PickupCellView pickupCell,
            List<DriverSuggestionView> driverSuggestions,
            List<MarketplaceEdgeView> marketplaceEdges,
            List<Map<String, Object>> riderCopilot,
            RouteIntelligenceDemoProofRunner.ProofCaseExecution proofComparison,
            String explanation
    ) {}

    public record PickupCellView(
            String cellId,
            String serviceTier,
            double centerLat,
            double centerLng,
            double demandForecast10m,
            double trafficForecast10m,
            double shortageForecast10m,
            double postDropOpportunity10m,
            double emptyZoneRisk10m,
            double compositeValue
    ) {}

    public record DriverSuggestionView(
            String driverId,
            String currentCellId,
            String targetCellId,
            double futureValueScore,
            double postDropOpportunity,
            double emptyZoneRisk,
            String recommendation
    ) {}

    public record MarketplaceEdgeView(
            String edgeId,
            String driverId,
            String orderId,
            String pickupCellId,
            String dropoffCellId,
            double pickupEtaMinutes,
            double deadheadKm,
            double edgeScore,
            double continuationScore,
            boolean borrowed,
            String rationale
    ) {}
}
