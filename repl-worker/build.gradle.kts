plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.power.assert)
    id("io.ktor.plugin")
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation(project(":repl-shared"))
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic.jdk8)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(kotlin("reflect"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

val testKotlinStdlib2 by configurations.creating

dependencies {
    // renovate: ignore-next-line
    testKotlinStdlib2("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}

tasks.test {
    val stdlibJar = configurations.runtimeClasspath.get().find { it.name.startsWith("kotlin-stdlib-") && !it.name.contains("jdk") }
    if (stdlibJar != null) {
        systemProperty("kotlin.stdlib.path", stdlibJar.absolutePath)
    }
    systemProperty("kotlin.stdlib.kotlin2.path", testKotlinStdlib2.joinToString(File.pathSeparator) { it.absolutePath })
    systemProperty("GRADLE_MCP_LOG_DIR", layout.buildDirectory.dir("test-logs").get().asFile.absolutePath)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "dev.rnett.gradle.mcp.repl.ReplWorker"
    }
}
