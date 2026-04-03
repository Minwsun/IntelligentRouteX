package com.routechain.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponsiveLayoutProfilesTest {

    @Test
    void wideViewportKeepsBothRailsVisible() {
        ControlRoomViewportProfile profile = ResponsiveLayoutProfiles.profileFor(1920, 1080);

        assertFalse(profile.collapseLeft());
        assertFalse(profile.collapseRight());
        assertTrue(profile.leftRailWidth() >= 260.0);
        assertTrue(profile.rightRailWidth() >= 280.0);
    }

    @Test
    void compactViewportCollapsesRightRail() {
        ControlRoomViewportProfile profile = ResponsiveLayoutProfiles.profileFor(1366, 768);

        assertFalse(profile.collapseLeft());
        assertTrue(profile.collapseRight());
        assertTrue(profile.rightRailWidth() <= 300.0);
    }
}
