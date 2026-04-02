package com.routechain.domain;

/**
 * Enumerations for the RouteChain domain.
 */
public final class Enums {
    private Enums() {}

    public enum OrderStatus {
        QUOTED, CONFIRMED, PENDING_ASSIGNMENT, ASSIGNED,
        PICKUP_EN_ROUTE, PICKED_UP, DROPOFF_EN_ROUTE,
        DELIVERED, CANCELLED, FAILED, EXPIRED
    }

    public enum DriverState {
        OFFLINE, ONLINE_IDLE, RESERVED, ROUTE_PENDING, PICKUP_EN_ROUTE,
        WAITING_PICKUP, DELIVERING, REPOSITIONING
    }

    public enum WeatherProfile {
        CLEAR, LIGHT_RAIN, HEAVY_RAIN, STORM
    }

    public enum SurgeSeverity {
        NORMAL, MEDIUM, HIGH, CRITICAL
    }

    public enum MetricSeverity {
        NORMAL, WARNING, CRITICAL
    }

    public enum SimulationLifecycle {
        IDLE, LOADING, RUNNING, PAUSED, COMPLETED, ERROR
    }

    public enum AlertType {
        CONGESTION, SURGE, WEATHER, DRIVER_SHORTAGE, DISPATCH, SYSTEM
    }

    public enum VehicleType {
        MOTORBIKE, CAR
    }

    public enum DeliveryServiceTier {
        INSTANT("instant"),
        TWO_HOUR("2h"),
        FOUR_HOUR("4h"),
        MULTI_STOP_COD("multi_stop_cod"),
        SCHEDULED("scheduled");

        private final String wireValue;

        DeliveryServiceTier(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        public static DeliveryServiceTier fromWireValue(String value) {
            if (value == null || value.isBlank()) {
                return INSTANT;
            }
            String normalized = value.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            return switch (normalized) {
                case "instant", "food", "food_delivery", "express" -> INSTANT;
                case "2h", "two_hour", "twohour", "two_hours" -> TWO_HOUR;
                case "4h", "four_hour", "fourhour", "scheduled_4h" -> FOUR_HOUR;
                case "multi_stop_cod", "multi_stop", "cod", "multi_cod" -> MULTI_STOP_COD;
                case "scheduled", "same_day" -> SCHEDULED;
                default -> INSTANT;
            };
        }

        public static DeliveryServiceTier classify(Order order) {
            if (order == null) {
                return INSTANT;
            }
            String serviceType = order.getServiceType();
            if (serviceType != null && !serviceType.isBlank() && !"standard".equalsIgnoreCase(serviceType)) {
                return fromWireValue(serviceType);
            }
            int etaMinutes = order.getPromisedEtaMinutes();
            if (etaMinutes <= 30) {
                return INSTANT;
            }
            if (etaMinutes <= 120) {
                return TWO_HOUR;
            }
            if (etaMinutes <= 240) {
                return FOUR_HOUR;
            }
            return SCHEDULED;
        }

        public static DeliveryServiceTier dominantForOrders(java.util.Collection<Order> orders, String scenarioName) {
            if (orders != null && !orders.isEmpty()) {
                java.util.Map<DeliveryServiceTier, Integer> counts = new java.util.EnumMap<>(DeliveryServiceTier.class);
                for (Order order : orders) {
                    counts.merge(classify(order), 1, Integer::sum);
                }
                return counts.entrySet().stream()
                        .max(java.util.Map.Entry.comparingByValue())
                        .map(java.util.Map.Entry::getKey)
                        .orElse(classifyScenario(scenarioName));
            }
            return classifyScenario(scenarioName);
        }

        public static DeliveryServiceTier classifyScenario(String scenarioName) {
            if (scenarioName == null || scenarioName.isBlank()) {
                return INSTANT;
            }
            String normalized = scenarioName.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("multi-stop") || normalized.contains("multi_stop")
                    || normalized.contains("cod")) {
                return MULTI_STOP_COD;
            }
            if (normalized.contains("4h") || normalized.contains("four_hour")
                    || normalized.contains("consolidation")) {
                return FOUR_HOUR;
            }
            if (normalized.contains("2h") || normalized.contains("two_hour")
                    || normalized.contains("fill_wave")) {
                return TWO_HOUR;
            }
            if (normalized.contains("scheduled")) {
                return SCHEDULED;
            }
            return INSTANT;
        }
    }
}
