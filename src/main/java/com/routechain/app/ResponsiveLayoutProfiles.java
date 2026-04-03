package com.routechain.app;

/**
 * Calculates control-room responsive layout sizes.
 */
public final class ResponsiveLayoutProfiles {
    private ResponsiveLayoutProfiles() {}

    public static ControlRoomViewportProfile profileFor(double sceneWidth, double sceneHeight) {
        double safeWidth = Math.max(1100.0, sceneWidth);
        double safeHeight = Math.max(700.0, sceneHeight);
        boolean collapseLeft = safeWidth < 1220.0;
        boolean collapseRight = safeWidth < 1450.0;
        double leftRail = clamp(safeWidth * 0.22, 260.0, 360.0);
        double rightRail = clamp(safeWidth * 0.23, 280.0, 360.0);
        if (collapseLeft) {
            leftRail = clamp(safeWidth * 0.18, 220.0, 300.0);
        }
        if (collapseRight) {
            rightRail = clamp(safeWidth * 0.18, 220.0, 300.0);
        }
        return new ControlRoomViewportProfile(
                sceneWidth,
                sceneHeight,
                leftRail,
                rightRail,
                collapseLeft,
                collapseRight
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
