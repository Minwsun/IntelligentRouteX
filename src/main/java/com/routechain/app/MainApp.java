package com.routechain.app;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.domain.Enums.*;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events;
import com.routechain.infra.MapBridge;
import com.routechain.simulation.SimulationEngine;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import com.routechain.infra.NativeMapPane;
import javafx.stage.Stage;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * RouteChain AI — Main JavaFX Application.
 * Single-screen Ops Console with map, floating glass cards, and real-time simulation.
 */
public class MainApp extends Application {

    private final SimulationEngine simEngine = new SimulationEngine();
    private final EventBus eventBus = EventBus.getInstance();
    private MapBridge mapBridge;
    private ScheduledExecutorService uiUpdater;

    // ── Observable state ────────────────────────────────────────────────
    private final StringProperty simStatusText = new SimpleStringProperty("IDLE");
    private final DoubleProperty onTimePercent = new SimpleDoubleProperty(100);
    private final DoubleProperty deadheadPercent = new SimpleDoubleProperty(0);
    private final DoubleProperty netPerHour = new SimpleDoubleProperty(0);
    private final IntegerProperty activeOrdersCount = new SimpleIntegerProperty(0);
    private final IntegerProperty activeDriversCount = new SimpleIntegerProperty(0);
    private final IntegerProperty completedOrdersCount = new SimpleIntegerProperty(0);
    private final StringProperty clockText = new SimpleStringProperty("12:00");
    private final StringProperty aiInsightTitle = new SimpleStringProperty("AI INSIGHT");
    private final StringProperty aiInsightText = new SimpleStringProperty("System ready. Press RUN SIMULATION to begin.");
    private final StringProperty aiInsightDelta = new SimpleStringProperty("");
    private final ObservableList<String> alertMessages = FXCollections.observableArrayList();
    private final BooleanProperty showDriverPanel = new SimpleBooleanProperty(false);
    private final StringProperty selectedDriverName = new SimpleStringProperty("");
    private final StringProperty selectedDriverStatus = new SimpleStringProperty("");
    private final StringProperty selectedDriverOrders = new SimpleStringProperty("0");
    private final StringProperty selectedDriverEta = new SimpleStringProperty("—");
    private final StringProperty selectedDriverDeadhead = new SimpleStringProperty("—");
    private final StringProperty selectedDriverEarnings = new SimpleStringProperty("—");

    // Layer visibility
    private final BooleanProperty trafficLayerOn = new SimpleBooleanProperty(true);
    private final BooleanProperty weatherLayerOn = new SimpleBooleanProperty(true);
    private final BooleanProperty driversLayerOn = new SimpleBooleanProperty(true);
    private final BooleanProperty orderHeatLayerOn = new SimpleBooleanProperty(true);
    private final BooleanProperty routesLayerOn = new SimpleBooleanProperty(true);

    // Scenario labels (mutated when controls change)
    private Label weatherValueLabel;
    private Label demandValueLabel;
    private Label trafficValueLabel;

    private Slider trafficSlider;
    private Slider demandSlider;
    private HBox weatherBtnBox;


    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane();
        root.getStyleClass().add("app-shell");

        // ── Map (base layer) ────────────────────────────────────────────
        NativeMapPane nativeMap = new NativeMapPane();
        nativeMap.getStyleClass().add("map-container");

        mapBridge = new MapBridge(nativeMap);
        mapBridge.attach();
        mapBridge.setOnDriverSelected(this::onDriverSelected);
        mapBridge.setDriverLookup(id -> simEngine.getDrivers().stream()
                .filter(d -> d.getId().equals(id)).findFirst().orElse(null));
        mapBridge.setOnMapReady(() -> {
            System.out.println("[App] Map ready — road network will push on first tick");
        });

        // ── Header ──────────────────────────────────────────────────────
        HBox header = createHeader();

        // ── Left floating cards (scrollable) ────────────────────────────
        VBox leftStack = new VBox(16);
        leftStack.setMaxWidth(320);
        leftStack.setPrefWidth(320);
        leftStack.getChildren().addAll(
                createModeCard(),
                createKpiBar(),
                createCountersCard(),
                createCollapsibleControlsCard(),
                createDriverFocusCard()
        );

        ScrollPane leftScroll = new ScrollPane(leftStack);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setMaxWidth(336);
        leftScroll.setPrefWidth(336);
        leftScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        leftScroll.getStyleClass().add("dark-scroll");

        // ── Right floating cards ────────────────────────────────────────
        VBox rightStack = new VBox(16);
        rightStack.setMaxWidth(340);
        rightStack.setPrefWidth(340);
        rightStack.getChildren().addAll(
                createAiInsightCard(),
                createScenarioCard()
        );

        // ── Floating layout ─────────────────────────────────────────────
        HBox leftColumn = new HBox(12, leftScroll);
        leftColumn.setAlignment(Pos.TOP_LEFT);
        leftColumn.setPickOnBounds(false);
        leftColumn.setPadding(new Insets(80, 0, 24, 16));

        VBox rightColumn = new VBox(16, rightStack);
        rightColumn.setAlignment(Pos.TOP_RIGHT);
        rightColumn.setPickOnBounds(false);
        rightColumn.setPadding(new Insets(80, 16, 24, 0));

        VBox headerBox = new VBox(header);
        headerBox.setAlignment(Pos.TOP_CENTER);
        headerBox.setPickOnBounds(false);

        BorderPane floatingLayer = new BorderPane();
        floatingLayer.setTop(headerBox);
        floatingLayer.setLeft(leftColumn);
        floatingLayer.setRight(rightColumn);
        floatingLayer.setPickOnBounds(false);

        root.getChildren().addAll(nativeMap, floatingLayer);

        // ── Scene & Stage ───────────────────────────────────────────────
        Scene scene = new Scene(root, 1600, 960);
        scene.getStylesheets().add(getClass().getResource("/css/app-theme.css").toExternalForm());
        scene.setFill(Color.valueOf("#0c0e10"));

        stage.setTitle("RouteChain AI — Delivery Intelligence");
        stage.setScene(scene);
        stage.setMinWidth(1280);
        stage.setMinHeight(800);
        stage.setOnCloseRequest(e -> {
            simEngine.stop();
            if (uiUpdater != null) uiUpdater.shutdownNow();
            Platform.exit();
            System.exit(0);
        });
        stage.show();

        // ── Subscribe to events ─────────────────────────────────────────
        subscribeToEvents();

        // ── Clock updater ───────────────────────────────────────────────
        uiUpdater = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ui-updater");
            t.setDaemon(true);
            return t;
        });
        uiUpdater.scheduleAtFixedRate(() -> {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            Platform.runLater(() -> clockText.set(time));
        }, 0, 15, TimeUnit.SECONDS);
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPONENT BUILDERS
    // ═══════════════════════════════════════════════════════════════════

    private HBox createHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("header-strip");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(24);

        Label title = new Label("ROUTECHAIN AI");
        title.getStyleClass().add("header-title");

        Label navFleet = new Label("FLEET");
        navFleet.getStyleClass().add("header-nav-link-active");
        Label navForecast = new Label("FORECAST");
        navForecast.getStyleClass().add("header-nav-link");
        Label navScenarios = new Label("SCENARIOS");
        navScenarios.getStyleClass().add("header-nav-link");
        Label navAnalytics = new Label("ANALYTICS");
        navAnalytics.getStyleClass().add("header-nav-link");
        HBox nav = new HBox(20, navFleet, navForecast, navScenarios, navAnalytics);
        nav.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField search = new TextField();
        search.setPromptText("Search coordinates...");
        search.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 999; "
                + "-fx-text-fill: #aaabad; -fx-prompt-text-fill: #535557; -fx-padding: 8 16; "
                + "-fx-border-color: rgba(70,72,74,0.1); -fx-border-radius: 999; -fx-font-size: 12px;");
        search.setPrefWidth(220);

        // Sim status badge
        Label statusBadge = new Label();
        statusBadge.getStyleClass().add("badge-info");
        statusBadge.textProperty().bind(simStatusText);

        Label bellIcon = new Label("🔔");
        bellIcon.setStyle("-fx-font-size: 16; -fx-text-fill: #aaabad; -fx-cursor: hand;");
        Label gearIcon = new Label("⚙");
        gearIcon.setStyle("-fx-font-size: 16; -fx-text-fill: #aaabad; -fx-cursor: hand;");

        StackPane avatar = new StackPane();
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);
        avatar.setStyle("-fx-background-color: #1d2022; -fx-background-radius: 999; "
                + "-fx-border-color: rgba(70,72,74,0.2); -fx-border-radius: 999;");
        Label avatarText = new Label("OP");
        avatarText.setStyle("-fx-text-fill: #99f7ff; -fx-font-size: 10; -fx-font-weight: bold;");
        avatar.getChildren().add(avatarText);

        header.getChildren().addAll(title, nav, spacer, search, statusBadge, bellIcon, gearIcon, avatar);
        return header;
    }

    private VBox createCollapsibleControlsCard() {
        VBox card = new VBox(0);
        card.getStyleClass().add("glass-card");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 14, 16));
        header.setCursor(javafx.scene.Cursor.HAND);

        Label icon = new Label("⚙");
        icon.setStyle("-fx-font-size: 16; -fx-text-fill: #99f7ff;");

        Label title = new Label(" MAP LAYERS");
        title.getStyleClass().add("text-title");
        title.setPadding(new Insets(0, 0, 0, 8));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label toggleIcon = new Label("▼");
        toggleIcon.setStyle("-fx-text-fill: #aaabad; -fx-font-size: 12px;");

        header.getChildren().addAll(icon, title, spacer, toggleIcon);

        VBox contentContainer = new VBox(16);
        contentContainer.setPadding(new Insets(0, 16, 16, 16));

        VBox layerBox = createLayerBox();

        contentContainer.getChildren().addAll(layerBox);
        contentContainer.setVisible(false);
        contentContainer.setManaged(false);

        header.setOnMouseClicked(e -> {
            boolean isExpanded = contentContainer.isVisible();
            contentContainer.setVisible(!isExpanded);
            contentContainer.setManaged(!isExpanded);
            toggleIcon.setText(isExpanded ? "▼" : "▲");
        });

        card.getChildren().addAll(header, contentContainer);
        return card;
    }

    private VBox createModeCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("glass-card-compact");

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label clock = new Label();
        clock.getStyleClass().add("metric-value-primary");
        clock.textProperty().bind(clockText);

        Label pmLabel = new Label("PM");
        pmLabel.getStyleClass().add("label-eyebrow");
        pmLabel.setPadding(new Insets(10, 0, 0, 4));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label liveBadge = new Label("LIVE");
        liveBadge.getStyleClass().add("badge-live");

        topRow.getChildren().addAll(clock, pmLabel, spacer, liveBadge);

        // Active counts
        HBox counters = new HBox(16);
        counters.setAlignment(Pos.CENTER_LEFT);
        Label ordersLabel = new Label();
        ordersLabel.setStyle("-fx-text-fill: #aaabad; -fx-font-size: 11;");
        ordersLabel.textProperty().bind(activeOrdersCount.asString("📦 %d active"));
        Label driversLabel = new Label();
        driversLabel.setStyle("-fx-text-fill: #aaabad; -fx-font-size: 11;");
        driversLabel.textProperty().bind(activeDriversCount.asString("🏍 %d drivers"));
        counters.getChildren().addAll(ordersLabel, driversLabel);

        card.getChildren().addAll(topRow, counters);
        return card;
    }

    private VBox createAiInsightCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("glass-card-accent");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(36, 36);
        iconCircle.setMaxSize(36, 36);
        iconCircle.setStyle("-fx-background-color: rgba(153,247,255,0.1); -fx-background-radius: 999;");
        Label icon = new Label("🧠");
        icon.setStyle("-fx-font-size: 16;");
        iconCircle.getChildren().add(icon);

        VBox textCol = new VBox(2);
        Label eyebrow = new Label();
        eyebrow.getStyleClass().add("label-eyebrow-primary");
        eyebrow.textProperty().bind(aiInsightTitle);

        Label body = new Label();
        body.getStyleClass().add("text-body");
        body.setWrapText(true);
        body.setMaxWidth(240);
        body.textProperty().bind(aiInsightText);

        Label delta = new Label();
        delta.getStyleClass().add("metric-delta-positive");
        delta.textProperty().bind(aiInsightDelta);

        textCol.getChildren().addAll(eyebrow, body, delta);
        header.getChildren().addAll(iconCircle, textCol);
        card.getChildren().add(header);
        return card;
    }

    private VBox createScenarioCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("glass-card");

        // Title
        HBox titleRow = new HBox();
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Scenario / Edit Tools");
        title.getStyleClass().add("text-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label version = new Label("v1.0");
        version.getStyleClass().add("label-eyebrow");
        titleRow.getChildren().addAll(title, spacer, version);

        // ── Preset Levels ───────────────────────────────────────────────
        VBox presetBox = new VBox(6);
        Label pLabel = new Label("SIMULATION PRESETS");
        pLabel.getStyleClass().add("label-eyebrow");

        HBox presetBtns = new HBox(6);
        String[] presets = {"LOW", "MEDIUM", "HIGH", "EXTREME"};
        for (String p : presets) {
            Button btn = new Button(p);
            btn.getStyleClass().add("secondary-button-small");
            btn.setPrefWidth(70);
            btn.setOnAction(e -> applyScenarioPreset(p));
            presetBtns.getChildren().add(btn);
        }
        presetBox.getChildren().addAll(pLabel, presetBtns);

        // ── Traffic intensity slider ────────────────────────────────────
        VBox trafficBox = new VBox(6);
        HBox trafficLabelRow = new HBox();
        trafficLabelRow.setAlignment(Pos.CENTER_LEFT);
        Label tLabel = new Label("TRAFFIC INTENSITY");
        tLabel.getStyleClass().add("label-eyebrow");
        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        trafficValueLabel = new Label("+42% PEAK");
        trafficValueLabel.getStyleClass().add("metric-delta-negative");
        trafficLabelRow.getChildren().addAll(tLabel, sp1, trafficValueLabel);

        trafficSlider = new Slider(0, 1, 0.42);
        trafficSlider.setStyle("-fx-control-inner-background: #232629;");
        trafficSlider.valueProperty().addListener((obs, old, val) -> {
            double v = val.doubleValue();
            simEngine.setTrafficIntensity(v);
            int pct = (int) (v * 100);
            trafficValueLabel.setText("+" + pct + "% PEAK");
            if (pct > 60) {
                trafficValueLabel.getStyleClass().setAll("metric-delta-negative");
            } else {
                trafficValueLabel.getStyleClass().setAll("metric-delta-positive");
            }
        });
        trafficBox.getChildren().addAll(trafficLabelRow, trafficSlider);

        // ── Weather profile selector ────────────────────────────────────
        VBox weatherBox = new VBox(6);
        Label wLabel = new Label("WEATHER PROFILE");
        wLabel.getStyleClass().add("label-eyebrow");
        weatherValueLabel = new Label("☀ Clear");
        weatherValueLabel.setStyle("-fx-text-fill: #99f7ff; -fx-font-size: 13px; -fx-font-weight: 500;");

        weatherBtnBox = new HBox(6);
        String[][] weatherOptions = {
                {"☀", "CLEAR"}, {"🌦", "LIGHT_RAIN"}, {"🌧", "HEAVY_RAIN"}, {"⛈", "STORM"}
        };
        for (String[] opt : weatherOptions) {
            Button btn = new Button(opt[0]);
            btn.setStyle("-fx-background-color: #232629; -fx-text-fill: #aaabad; "
                    + "-fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 14;");
            btn.setOnAction(e -> updateWeatherUI(WeatherProfile.valueOf(opt[1])));
            weatherBtnBox.getChildren().add(btn);
        }
        // Init first button as selected
        highlightWeatherBtn(0);

        weatherBox.getChildren().addAll(wLabel, weatherValueLabel, weatherBtnBox);

        // ── Demand multiplier slider ────────────────────────────────────
        VBox demandBox = new VBox(6);
        HBox demandLabelRow = new HBox();
        demandLabelRow.setAlignment(Pos.CENTER_LEFT);
        Label dLabel = new Label("DEMAND LOAD");
        dLabel.getStyleClass().add("label-eyebrow");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        demandValueLabel = new Label("×1.0 Normal");
        demandValueLabel.setStyle("-fx-text-fill: #00ffab; -fx-font-size: 11px; -fx-font-weight: 600;");
        demandLabelRow.getChildren().addAll(dLabel, sp2, demandValueLabel);

        demandSlider = new Slider(0.2, 4.0, 1.0);
        demandSlider.setStyle("-fx-control-inner-background: #232629;");
        demandSlider.valueProperty().addListener((obs, old, val) -> {
            double v = val.doubleValue();
            simEngine.setDemandMultiplier(v);
            String text;
            if (v < 0.6) text = String.format("×%.1f Low", v);
            else if (v < 1.4) text = String.format("×%.1f Normal", v);
            else if (v < 2.5) text = String.format("×%.1f Elevated", v);
            else text = String.format("×%.1f Extreme", v);
            demandValueLabel.setText(text);
        });
        demandBox.getChildren().addAll(demandLabelRow, demandSlider);

        // ── Run Simulation button ───────────────────────────────────────
        Button runBtn = new Button("▶  RUN SIMULATION");
        runBtn.getStyleClass().add("primary-button");
        runBtn.setMaxWidth(Double.MAX_VALUE);
        runBtn.setOnAction(e -> toggleSimulation(runBtn));

        card.getChildren().addAll(titleRow, presetBox, new Separator(), trafficBox, weatherBox, demandBox, runBtn);
        return card;
    }

    private void updateWeatherUI(WeatherProfile wp) {
        simEngine.setWeatherProfile(wp);
        String label = switch (wp) {
            case CLEAR -> "☀ Clear";
            case LIGHT_RAIN -> "🌦 Light Rain";
            case HEAVY_RAIN -> "🌧 Heavy Rain";
            case STORM -> "⛈ Storm";
        };
        weatherValueLabel.setText(label);
        int index = wp.ordinal();
        highlightWeatherBtn(index);
    }

    private void highlightWeatherBtn(int index) {
        for (int i = 0; i < weatherBtnBox.getChildren().size(); i++) {
            Button b = (Button) weatherBtnBox.getChildren().get(i);
            if (i == index) {
                b.setStyle("-fx-background-color: rgba(153,247,255,0.2); -fx-text-fill: #99f7ff; "
                        + "-fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 14; "
                        + "-fx-border-color: rgba(153,247,255,0.3); -fx-border-radius: 8;");
            } else {
                b.setStyle("-fx-background-color: #232629; -fx-text-fill: #aaabad; "
                        + "-fx-background-radius: 8; -fx-padding: 6 12; -fx-cursor: hand; -fx-font-size: 14;");
            }
        }
    }

    private void applyScenarioPreset(String p) {
        simEngine.reset();
        switch (p) {
            case "LOW" -> {
                simEngine.setInitialDriverCount(1);
                trafficSlider.setValue(0.1);
                demandSlider.setValue(0.5);
                updateWeatherUI(WeatherProfile.CLEAR);
            }
            case "MEDIUM" -> {
                simEngine.setInitialDriverCount(35);
                trafficSlider.setValue(0.4);
                demandSlider.setValue(1.0);
                updateWeatherUI(WeatherProfile.CLEAR);
            }
            case "HIGH" -> {
                simEngine.setInitialDriverCount(100);
                trafficSlider.setValue(0.7);
                demandSlider.setValue(2.2);
                updateWeatherUI(WeatherProfile.LIGHT_RAIN);
            }
            case "EXTREME" -> {
                simEngine.setInitialDriverCount(250);
                trafficSlider.setValue(0.95);
                demandSlider.setValue(3.8);
                updateWeatherUI(WeatherProfile.STORM);
            }
        }
        // Auto-start simulation in the new mode
        simEngine.start();
        simStatusText.set("RUNNING");
    }

    private VBox createKpiBar() {
        VBox card = new VBox(0);
        card.getStyleClass().add("glass-card-compact");

        HBox kpiRow = new HBox(0);
        kpiRow.setAlignment(Pos.CENTER);

        VBox onTimePct = createKpiItem("ON-TIME", onTimePercent, "%", "secondary");
        Separator sep1 = createVerticalSep();
        VBox deadheadPct = createKpiItem("DEADHEAD", deadheadPercent, "%", "error");
        Separator sep2 = createVerticalSep();

        VBox netHrBox = new VBox(4);
        netHrBox.setAlignment(Pos.CENTER);
        netHrBox.setPadding(new Insets(0, 12, 0, 12));
        Label netLabel = new Label("NET/HR");
        netLabel.getStyleClass().add("label-eyebrow");
        Label netVal = new Label();
        netVal.getStyleClass().add("metric-value-medium");
        netVal.textProperty().bind(netPerHour.asString("%.0fk"));
        netHrBox.getChildren().addAll(netLabel, netVal);

        kpiRow.getChildren().addAll(onTimePct, sep1, deadheadPct, sep2, netHrBox);
        card.getChildren().add(kpiRow);
        return card;
    }

    private VBox createCountersCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("glass-card-compact");

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        VBox delivered = new VBox(2);
        delivered.setAlignment(Pos.CENTER);
        Label dlLabel = new Label("DELIVERED");
        dlLabel.getStyleClass().add("label-eyebrow");
        Label dlVal = new Label();
        dlVal.getStyleClass().add("metric-value-medium");
        dlVal.setStyle("-fx-text-fill: #00ffab;");
        dlVal.textProperty().bind(completedOrdersCount.asString("%d"));
        delivered.getChildren().addAll(dlLabel, dlVal);

        VBox active = new VBox(2);
        active.setAlignment(Pos.CENTER);
        Label acLabel = new Label("ACTIVE");
        acLabel.getStyleClass().add("label-eyebrow");
        Label acVal = new Label();
        acVal.getStyleClass().add("metric-value-medium");
        acVal.setStyle("-fx-text-fill: #99f7ff;");
        acVal.textProperty().bind(activeOrdersCount.asString("%d"));
        active.getChildren().addAll(acLabel, acVal);

        VBox drvrs = new VBox(2);
        drvrs.setAlignment(Pos.CENTER);
        Label drLabel = new Label("DRIVERS");
        drLabel.getStyleClass().add("label-eyebrow");
        Label drVal = new Label();
        drVal.getStyleClass().add("metric-value-medium");
        drVal.textProperty().bind(activeDriversCount.asString("%d"));
        drvrs.getChildren().addAll(drLabel, drVal);

        row.getChildren().addAll(delivered, active, drvrs);
        card.getChildren().add(row);
        return card;
    }

    private VBox createKpiItem(String label, DoubleProperty value, String unit, String colorClass) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(0, 12, 0, 12));
        HBox.setHgrow(box, Priority.ALWAYS);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("label-eyebrow");
        Label val = new Label();
        val.getStyleClass().add("metric-value-" + colorClass);
        val.textProperty().bind(value.asString("%.0f").concat(unit));
        box.getChildren().addAll(lbl, val);
        return box;
    }

    private Separator createVerticalSep() {
        Separator sep = new Separator();
        sep.setOrientation(Orientation.VERTICAL);
        sep.setStyle("-fx-background-color: rgba(70,72,74,0.3); -fx-max-width: 1;");
        sep.setPrefHeight(40);
        return sep;
    }

    private VBox createLayerBox() {
        VBox card = new VBox(10);

        Label title = new Label("VIEW LAYERS");
        title.getStyleClass().add("label-eyebrow");
        title.setPadding(new Insets(0, 0, 6, 0));
        title.setStyle("-fx-border-color: transparent transparent rgba(70,72,74,0.2) transparent; "
                + "-fx-border-width: 0 0 1 0; -fx-padding: 0 0 8 0;");
        card.getChildren().add(title);

        card.getChildren().addAll(
                createLayerToggle("Traffic", trafficLayerOn, "traffic"),
                createLayerToggle("Weather", weatherLayerOn, "weather"),
                createLayerToggle("Order Heat", orderHeatLayerOn, "orderHeat"),
                createLayerToggle("Drivers", driversLayerOn, "drivers"),
                createLayerToggle("Routes", routesLayerOn, "routes")
        );
        return card;
    }

    private HBox createLayerToggle(String name, BooleanProperty prop, String mapLayer) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setSpacing(8);
        row.setPadding(new Insets(2, 0, 2, 0));
        row.setCursor(javafx.scene.Cursor.HAND);

        Label label = new Label(name);
        label.setStyle("-fx-text-fill: #aaabad; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StackPane track = new StackPane();
        track.getStyleClass().add(prop.get() ? "toggle-track-on" : "toggle-track-off");

        StackPane thumb = new StackPane();
        thumb.getStyleClass().add("toggle-thumb");
        thumb.setTranslateX(prop.get() ? 9 : -9);

        track.getChildren().add(thumb);
        row.getChildren().addAll(label, spacer, track);

        row.setOnMouseClicked(e -> {
            prop.set(!prop.get());
            boolean on = prop.get();
            track.getStyleClass().setAll(on ? "toggle-track-on" : "toggle-track-off");
            thumb.setTranslateX(on ? 9 : -9);
            label.setStyle("-fx-text-fill: " + (on ? "#aaabad" : "#535557") + "; -fx-font-size: 12px;");
            if (mapBridge != null) mapBridge.toggleLayer(mapLayer, on);
        });

        return row;
    }

    private VBox createDriverFocusCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("glass-card");
        card.visibleProperty().bind(showDriverPanel);
        card.managedProperty().bind(showDriverPanel);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        avatar.setMinSize(48, 48);
        avatar.setMaxSize(48, 48);
        avatar.setStyle("-fx-background-color: #1d2022; -fx-background-radius: 999; "
                + "-fx-border-color: rgba(70,72,74,0.3); -fx-border-radius: 999;");
        Label avatarIcon = new Label("🏍");
        avatarIcon.setStyle("-fx-font-size: 20;");
        avatar.getChildren().add(avatarIcon);

        VBox nameCol = new VBox(2);
        Label driverName = new Label();
        driverName.getStyleClass().add("text-title");
        driverName.textProperty().bind(selectedDriverName);
        Label driverStatus = new Label();
        driverStatus.getStyleClass().add("label-eyebrow-primary");
        driverStatus.textProperty().bind(selectedDriverStatus);
        nameCol.getChildren().addAll(driverName, driverStatus);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: #aaabad; "
                + "-fx-background-radius: 999; -fx-min-width: 28; -fx-min-height: 28; "
                + "-fx-max-width: 28; -fx-max-height: 28; -fx-cursor: hand; -fx-font-size: 12;");
        closeBtn.setOnAction(e -> showDriverPanel.set(false));

        header.getChildren().addAll(avatar, nameCol, sp, closeBtn);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);
        grid.add(createDriverStat("ORDERS", selectedDriverOrders), 0, 0);
        grid.add(createDriverStat("ETA", selectedDriverEta), 1, 0);
        grid.add(createDriverStat("DEADHEAD", selectedDriverDeadhead), 0, 1);
        grid.add(createDriverStat("EARNINGS", selectedDriverEarnings), 1, 1);

        card.getChildren().addAll(header, grid);
        return card;
    }

    private VBox createDriverStat(String label, StringProperty value) {
        VBox box = new VBox(2);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-family: 'Space Grotesk'; -fx-font-size: 9; -fx-font-weight: 700; "
                + "-fx-text-fill: #aaabad;");
        Label val = new Label();
        val.setStyle("-fx-font-size: 14; -fx-font-weight: 500; -fx-text-fill: #eeeef0;");
        val.textProperty().bind(value);
        box.getChildren().addAll(lbl, val);
        return box;
    }


    // ═══════════════════════════════════════════════════════════════════
    // SIMULATION CONTROL
    // ═══════════════════════════════════════════════════════════════════

    private void toggleSimulation(Button runBtn) {
        if (simEngine.getLifecycle() == SimulationLifecycle.RUNNING) {
            simEngine.stop();
            Platform.runLater(() -> {
                runBtn.setText("▶  RUN SIMULATION");
                simStatusText.set("PAUSED");
            });
        } else {
            simEngine.start();
            Platform.runLater(() -> {
                runBtn.setText("⏸  PAUSE SIMULATION");
                simStatusText.set("RUNNING");
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EVENT SUBSCRIPTIONS
    // ═══════════════════════════════════════════════════════════════════

    private void subscribeToEvents() {
        // ── No individual per-event map calls ──────────────────────────
        // All map data is pushed in a single batched frame per tick.
        // Individual events (OrderCreated, etc.) no longer need to send
        // JS commands — the tick handler collects ALL state and flushes once.

        // Simulation tick → batch ALL map updates into a single frame
        eventBus.subscribe(Events.SimulationTick.class, e -> {
            if (mapBridge == null) return;

            // ── EVERY tick: driver positions (animation in JS handles smoothing) ──
            mapBridge.setDriverPositions(simEngine.getDrivers());

            // ── Every 3 ticks: request OSRM routes + build route GeoJSON ──
            if (e.tickNumber() % 3 == 0) {
                for (Driver d : simEngine.getDrivers()) {
                    if (d.getTargetLocation() != null) {
                        String phase = (d.getState() == com.routechain.domain.Enums.DriverState.PICKUP_EN_ROUTE)
                                ? "pickup" : "delivery";
                        mapBridge.requestDriverRoute(
                                d.getId(),
                                d.getCurrentLocation().lat(), d.getCurrentLocation().lng(),
                                d.getTargetLocation().lat(), d.getTargetLocation().lng(),
                                phase);
                    } else {
                        mapBridge.clearDriverRoute(d.getId());
                    }
                }
                mapBridge.buildAndSetRouteData();
            }

            // ── Every 15 ticks: traffic, weather, orders overlays ──
            if (e.tickNumber() % 15 == 0) {
                mapBridge.setTrafficData(simEngine.getCorridorSeverity());
                mapBridge.setWeatherData(simEngine.getRegions());
                mapBridge.setOrderData(simEngine.getActiveOrders());
            }

            // ── Road network: push once on first tick ──
            if (e.tickNumber() == 1) {
                mapBridge.setRoadNetworkData();
            }

            // ════════════════════════════════════════════════════════
            // FLUSH: ONE single JS command with ALL accumulated data
            // ════════════════════════════════════════════════════════
            mapBridge.flushFrame();
        });

        // Metrics snapshot → KPI UI
        eventBus.subscribe(Events.MetricsSnapshot.class, e -> {
            Platform.runLater(() -> {
                onTimePercent.set(e.onTimePercent());
                deadheadPercent.set(e.deadheadPercent());
                netPerHour.set(e.netPerHour() / 1000.0);
                activeOrdersCount.set(e.activeOrders());
                activeDriversCount.set(e.activeDrivers());
                completedOrdersCount.set(e.completedOrders());
            });
        });

        // AI Insight
        eventBus.subscribe(Events.AiInsight.class, e -> {
            Platform.runLater(() -> {
                aiInsightTitle.set(e.title());
                aiInsightText.set(e.description());
                aiInsightDelta.set(e.recommendation());
            });
        });

        // Alerts
        eventBus.subscribe(Events.AlertRaised.class, e -> {
            Platform.runLater(() -> {
                alertMessages.add(0, e.title() + " — " + e.description());
                if (alertMessages.size() > 20) alertMessages.remove(alertMessages.size() - 1);
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // DRIVER SELECTION
    // ═══════════════════════════════════════════════════════════════════

    private void onDriverSelected(String driverId) {
        Driver driver = simEngine.getDrivers().stream()
                .filter(d -> d.getId().equals(driverId)).findFirst().orElse(null);
        if (driver == null) return;

        Platform.runLater(() -> {
            showDriverPanel.set(true);
            selectedDriverName.set(driver.getName());
            selectedDriverStatus.set(driver.getState().name().replace("_", " "));
            selectedDriverOrders.set(driver.getActiveOrderIds().size() + " Active");
            selectedDriverEta.set(driver.getSpeedKmh() > 0 ? String.format("%.0f km/h", driver.getSpeedKmh()) : "Idle");
            selectedDriverDeadhead.set(String.format("%.1f km", 0.0));

            // Earnings: accumulated + pending delivery fees
            double earned = driver.getNetEarningToday();
            double pendingFee = 0;
            for (Order o : simEngine.getActiveOrders()) {
                if (driver.getActiveOrderIds().contains(o.getId())) {
                    pendingFee += o.getQuotedFee();
                }
            }
            if (earned > 0 || pendingFee > 0) {
                String earningStr = String.format("%,.0f₫", earned);
                if (pendingFee > 0) {
                    earningStr += String.format(" (+%,.0f₫)", pendingFee);
                }
                selectedDriverEarnings.set(earningStr);
            } else {
                selectedDriverEarnings.set("Chưa có đơn");
            }
        });

        if (mapBridge != null) mapBridge.focusDriver(driverId);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        launch(args);
    }
}
