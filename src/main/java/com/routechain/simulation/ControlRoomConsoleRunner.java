package com.routechain.simulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prints the latest control-room markdown summary to the terminal.
 */
public final class ControlRoomConsoleRunner {
    private static final Path CONTROL_ROOM_MD =
            Path.of("build", "routechain-apex", "benchmarks", "control-room", "control_room_latest.md");

    private ControlRoomConsoleRunner() {}

    public static void main(String[] args) throws IOException {
        if (Files.notExists(CONTROL_ROOM_MD)) {
            System.out.println("[ControlRoom] No frame found yet. Run a benchmark first.");
            return;
        }
        System.out.println(Files.readString(CONTROL_ROOM_MD));
    }
}
