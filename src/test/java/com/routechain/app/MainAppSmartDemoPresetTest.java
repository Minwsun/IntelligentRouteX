package com.routechain.app;

import com.routechain.domain.Enums.SimulationLifecycle;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainAppSmartDemoPresetTest {

    @BeforeAll
    static void initJavaFxToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start in time");
    }

    @AfterEach
    void stopSharedLiveEngine() {
        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        engine.reset();
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void scenarioCardShouldExposeOnlyThreeDemoPresets() throws Exception {
        MainApp app = new MainApp();

        List<String> presetLabels = runOnFxThread(() -> {
            invoke(app, "createScenarioCard");
            Node card = (Node) invoke(app, "createScenarioCard");
            return collectButtonTexts(card).stream()
                    .filter(text -> "NORMAL".equals(text)
                            || "SMART_DEMO_3x10".equals(text)
                            || "DINNER_PEAK_HCMC".equals(text))
                    .toList();
        });

        assertIterableEquals(
                List.of("NORMAL", "SMART_DEMO_3x10", "DINNER_PEAK_HCMC"),
                presetLabels,
                "JavaFX scenario card should only expose the curated three demo presets");
    }

    @Test
    void smartDemoPresetShouldBootThreeDriversAndTenOrdersOnLiveCore() throws Exception {
        MainApp app = new MainApp();
        SimulationEngine engine = RouteCoreRuntime.liveEngine();

        runOnFxThread(() -> {
            invoke(app, "createScenarioCard");
            invoke(app, "applyScenarioPreset", "SMART_DEMO_3x10");
            return null;
        });

        assertEquals(SimulationLifecycle.RUNNING, engine.getLifecycle(), "Preset should start the live simulation");
        assertEquals(0, engine.getInitialDriverCount(), "Smart demo should rely only on injected drivers");
        assertEquals(3, engine.getDrivers().size(), "Smart demo should inject exactly three drivers");
        assertEquals(10, engine.getActiveOrders().size(), "Smart demo should inject exactly ten orders");
        assertEquals(0.0, engine.getDemandMultiplier(), 1e-9, "Smart demo should disable background random demand");
        assertEquals(WeatherProfile.CLEAR, engine.getWeatherProfile(), "Smart demo should stay in clear conditions");
        assertEquals("18:15", engine.getSimulatedTimeFormatted(), "Smart demo should start at the curated dinner-prep window");
    }

    private static List<String> collectButtonTexts(Node root) {
        List<String> texts = new ArrayList<>();
        collectButtonTexts(root, texts);
        return texts;
    }

    private static void collectButtonTexts(Node node, List<String> texts) {
        if (node instanceof Button button) {
            texts.add(button.getText());
        }
        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                collectButtonTexts(child, texts);
            }
        }
    }

    private static Object invoke(Object target, String methodName, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i].getClass();
        }
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static <T> T runOnFxThread(FxCallable<T> callable) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                value.set(callable.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for JavaFX task");
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(failure.get());
        }
        return value.get();
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
    }
}
