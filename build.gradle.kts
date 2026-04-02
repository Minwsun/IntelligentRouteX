plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.routechain"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.2"
    modules("javafx.controls", "javafx.web", "javafx.fxml")
}

dependencies {
    // JSON processing
    implementation("com.google.code.gson:gson:2.11.0")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    // AI-first optimization/runtime stack (production-small, Java-first)
    implementation("ai.timefold.solver:timefold-solver-core:1.16.0")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")

    // Stream backbone client (local-first now, cluster-ready later)
    implementation("org.apache.kafka:kafka-clients:4.2.0")
    implementation("com.uber:h3:4.1.1")
    runtimeOnly("com.google.ortools:ortools-java:9.10.4067")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set(project.findProperty("mainClass")?.toString() ?: "com.routechain.app.MainApp")
    applicationDefaultJvmArgs = listOf(
        "--add-modules", "javafx.controls,javafx.web",
        "-Dprism.dirtyopts=false"
    )
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("benchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.BenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("scenarioBatch") {
    group = "application"
    mainClass.set("com.routechain.simulation.ScenarioBatchRunner")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("omegaAblation") {
    group = "application"
    mainClass.set("com.routechain.simulation.ScenarioBatchRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("ablation")
}

tasks.register<JavaExec>("showcaseBatch") {
    group = "application"
    mainClass.set("com.routechain.simulation.ScenarioBatchRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("showcase")
}

tasks.register<JavaExec>("stressTuneBatch") {
    group = "application"
    mainClass.set("com.routechain.simulation.ScenarioBatchRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("stress")
}

tasks.register<JavaExec>("hybridBenchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.HybridBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("all")
}

tasks.register<JavaExec>("researchBenchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.HybridBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("research")
}

tasks.register<JavaExec>("counterfactualArena") {
    group = "application"
    mainClass.set("com.routechain.simulation.CounterfactualArenaRunner")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("counterfactualArenaSmoke") {
    group = "application"
    mainClass.set("com.routechain.simulation.CounterfactualArenaRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("smoke")
}

tasks.register<JavaExec>("performanceBenchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.PerformanceBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("all")
}

tasks.register<JavaExec>("microDispatchBenchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.PerformanceBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("micro")
}

tasks.register<JavaExec>("performanceBenchmarkSmoke") {
    group = "application"
    mainClass.set("com.routechain.simulation.PerformanceBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("smoke")
}

tasks.register<JavaExec>("soakBenchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.PerformanceBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("soak")
}

tasks.register<JavaExec>("controlRoomConsole") {
    group = "application"
    mainClass.set("com.routechain.simulation.ControlRoomConsoleRunner")
    classpath = sourceSets["main"].runtimeClasspath
}
