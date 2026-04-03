package com.routechain.app;

/**
 * Responsive layout profile for the JavaFX control-room.
 */
public record ControlRoomViewportProfile(
        double sceneWidth,
        double sceneHeight,
        double leftRailWidth,
        double rightRailWidth,
        boolean collapseLeft,
        boolean collapseRight
) {}
