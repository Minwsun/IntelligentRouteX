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
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.3.5"))

    // JSON processing
    implementation("com.google.code.gson:gson:2.11.0")

    // Database
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.data:spring-data-commons")

    // AI-first optimization/runtime stack (production-small, Java-first)
    implementation("ai.timefold.solver:timefold-solver-core:1.16.0")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.22.0")
    implementation("com.graphhopper:jsprit-core:1.9.0-beta.11")
    implementation("org.neo4j.driver:neo4j-java-driver:5.27.0")

    // Stream backbone client (local-first now, cluster-ready later)
    implementation("org.apache.kafka:kafka-clients:4.2.0")
    implementation("com.uber:h3:4.1.1")
    runtimeOnly("com.google.ortools:ortools-java:9.10.4067")
    runtimeOnly("org.postgresql:postgresql")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:kafka:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
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
    options.compilerArgs.add("-parameters")
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

val routeAiRegressionSmoke by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the route/AI smoke regression suite."
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.routechain.ai.OmegaDispatchBrainContractTest")
        includeTestsMatching("com.routechain.ai.OmegaStressRegimeTest")
        includeTestsMatching("com.routechain.ai.DriverPlanGeneratorProfileTest")
        includeTestsMatching("com.routechain.ai.PlanUtilityScorerSoftLandingTest")
        includeTestsMatching("com.routechain.ai.SpatiotemporalFieldForecastTest")
        includeTestsMatching("com.routechain.simulation.AssignmentSolverThreeOrderPolicyTest")
        includeTestsMatching("com.routechain.simulation.SimulationRoutePendingTest")
        includeTestsMatching("com.routechain.graph.GraphShadowProjectorStabilityTest")
    }
}

val routeAiCertificationSmokeSummary by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Summarizes and enforces the absolute smoke route gate with Legacy as reference."
    mainClass.set("com.routechain.simulation.RouteAiCertificationRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("smoke")
}

val cleanBenchmarkArtifacts by tasks.registering(Delete::class) {
    group = "verification"
    description = "Removes benchmark artifacts so each lane certifies a clean run."
    delete(layout.buildDirectory.dir("routechain-apex/benchmarks"))
}

val scenarioBatchCertification by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the seeded certification scenario batch."
    mainClass.set("com.routechain.simulation.ScenarioBatchRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("certification")
}

val scenarioBatchNightly by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the deep nightly scenario batch."
    mainClass.set("com.routechain.simulation.ScenarioBatchRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("nightly")
}

val hybridBenchmarkTrackA by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the production-realism Track A hybrid benchmark."
    mainClass.set("com.routechain.simulation.HybridBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("production")
}

val repoIntelligenceSmokeSummary by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Aggregates the smoke lane into one repo intelligence verdict."
    mainClass.set("com.routechain.simulation.RepoIntelligenceCertificationRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("smoke")
}

val repoIntelligenceCertificationSummary by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Aggregates the certification lane into one repo intelligence verdict."
    mainClass.set("com.routechain.simulation.RepoIntelligenceCertificationRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("certification")
}

val repoIntelligenceNightlySummary by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Aggregates the nightly lane into one repo intelligence verdict."
    mainClass.set("com.routechain.simulation.RepoIntelligenceCertificationRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("nightly")
}

tasks.named("performanceBenchmarkSmoke") {
    mustRunAfter(routeAiRegressionSmoke)
    mustRunAfter("microDispatchBenchmark")
    mustRunAfter(cleanBenchmarkArtifacts)
}

tasks.named("counterfactualArenaSmoke") {
    mustRunAfter("performanceBenchmarkSmoke")
    mustRunAfter(cleanBenchmarkArtifacts)
}

routeAiCertificationSmokeSummary.configure {
    mustRunAfter("counterfactualArenaSmoke")
    mustRunAfter(cleanBenchmarkArtifacts)
}

tasks.named("microDispatchBenchmark") {
    mustRunAfter(routeAiRegressionSmoke)
    mustRunAfter(cleanBenchmarkArtifacts)
}

tasks.named("test") {
    mustRunAfter(cleanBenchmarkArtifacts)
}

scenarioBatchCertification.configure {
    mustRunAfter("repoIntelligenceSmoke")
}

hybridBenchmarkTrackA.configure {
    mustRunAfter(scenarioBatchCertification)
}

repoIntelligenceSmokeSummary.configure {
    mustRunAfter(routeAiCertificationSmokeSummary)
}

repoIntelligenceCertificationSummary.configure {
    mustRunAfter(hybridBenchmarkTrackA)
}

scenarioBatchNightly.configure {
    mustRunAfter("repoIntelligenceCertification")
}

tasks.named("researchBenchmark") {
    mustRunAfter(scenarioBatchNightly)
}

tasks.named("omegaAblation") {
    mustRunAfter("researchBenchmark")
}

repoIntelligenceNightlySummary.configure {
    mustRunAfter("soakBenchmark")
}

tasks.register("routeAiCertificationSmoke") {
    group = "verification"
    description = "Runs route regressions, smoke benchmarks, and the absolute smoke certification gate."
    dependsOn(routeAiRegressionSmoke)
    dependsOn("performanceBenchmarkSmoke")
    dependsOn("counterfactualArenaSmoke")
    dependsOn(routeAiCertificationSmokeSummary)
}

tasks.register("repoIntelligenceSmoke") {
    group = "verification"
    description = "Runs the fast repo-wide intelligence smoke lane."
    dependsOn(cleanBenchmarkArtifacts)
    dependsOn("test")
    dependsOn(routeAiRegressionSmoke)
    dependsOn("microDispatchBenchmark")
    dependsOn("performanceBenchmarkSmoke")
    dependsOn("counterfactualArenaSmoke")
    dependsOn(routeAiCertificationSmokeSummary)
    dependsOn(repoIntelligenceSmokeSummary)
}

tasks.register("repoIntelligenceCertification") {
    group = "verification"
    description = "Runs the full repo-wide intelligence certification lane."
    dependsOn(cleanBenchmarkArtifacts)
    dependsOn("repoIntelligenceSmoke")
    dependsOn(scenarioBatchCertification)
    dependsOn(hybridBenchmarkTrackA)
    dependsOn(repoIntelligenceCertificationSummary)
}

tasks.register("repoIntelligenceNightly") {
    group = "verification"
    description = "Runs the deep nightly repo-wide intelligence certification lane."
    dependsOn(cleanBenchmarkArtifacts)
    dependsOn("repoIntelligenceCertification")
    dependsOn(scenarioBatchNightly)
    dependsOn("researchBenchmark")
    dependsOn("omegaAblation")
    dependsOn("soakBenchmark")
    dependsOn(repoIntelligenceNightlySummary)
}

tasks.register<JavaExec>("soakBenchmark") {
    group = "application"
    mainClass.set("com.routechain.simulation.PerformanceBenchmarkRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args("soak")
}

tasks.named("soakBenchmark") {
    mustRunAfter("omegaAblation")
}

tasks.register<JavaExec>("controlRoomConsole") {
    group = "application"
    mainClass.set("com.routechain.simulation.ControlRoomConsoleRunner")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("apiRun") {
    group = "application"
    mainClass.set("com.routechain.api.RouteChainApiApplication")
    classpath = sourceSets["main"].runtimeClasspath
}
