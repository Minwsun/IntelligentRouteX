package com.routechain.api.service;

import com.routechain.infra.AdminQueryService;
import com.routechain.infra.PlatformRuntimeBootstrap;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads control-room artifacts so mobile/ops APIs expose grounded backend data immediately.
 */
@Service
public class OpsArtifactService {
    private static final Path CONTROL_ROOM_ROOT = Path.of("build", "routechain-apex", "benchmarks", "control-room");

    public String latestControlRoomMarkdown() {
        return readText(CONTROL_ROOM_ROOT.resolve("control_room_latest.md"));
    }

    public AdminQueryService.SystemAdminSnapshot adminSnapshot() {
        return PlatformRuntimeBootstrap.getAdminQueryService().snapshot();
    }

    public List<Map<String, String>> cityTwinRows(int limit) {
        return readCsv(CONTROL_ROOM_ROOT.resolve("city_twin_cells.csv"), limit);
    }

    public List<Map<String, String>> marketplaceEdges(int limit) {
        return readCsv(CONTROL_ROOM_ROOT.resolve("marketplace_edges.csv"), limit);
    }

    public List<DriverCopilotRow> driverFutureValues(String driverId, int limit) {
        return readCsv(CONTROL_ROOM_ROOT.resolve("driver_future_values.csv"), limit * 4).stream()
                .filter(row -> driverId == null || driverId.isBlank() || driverId.equalsIgnoreCase(row.getOrDefault("driverId", "")))
                .limit(limit)
                .map(row -> new DriverCopilotRow(
                        row.getOrDefault("driverId", ""),
                        row.getOrDefault("targetCellId", ""),
                        parseDouble(row.get("futureValueScore")),
                        parseDouble(row.get("postDropOpportunity")),
                        parseDouble(row.get("emptyZoneRisk")),
                        row.getOrDefault("recommendation", "")
                ))
                .toList();
    }

    private String readText(Path path) {
        try {
            if (!Files.exists(path)) {
                return "";
            }
            return Files.readString(path);
        } catch (IOException e) {
            return "";
        }
    }

    private List<Map<String, String>> readCsv(Path path, int limit) {
        try {
            if (!Files.exists(path)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                return List.of();
            }
            String[] headers = lines.get(0).split(",");
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i < lines.size() && rows.size() < limit; i++) {
                String[] values = lines.get(i).split(",", -1);
                Map<String, String> row = new java.util.LinkedHashMap<>();
                for (int j = 0; j < headers.length && j < values.length; j++) {
                    row.put(headers[j], values[j]);
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    private double parseDouble(String raw) {
        try {
            return raw == null || raw.isBlank() ? 0.0 : Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    public record DriverCopilotRow(
            String driverId,
            String targetCellId,
            double futureValueScore,
            double postDropOpportunity,
            double emptyZoneRisk,
            String recommendation
    ) {}
}
