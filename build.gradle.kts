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
