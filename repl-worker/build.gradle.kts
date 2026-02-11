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
    implementation(libs.slf4j.simple)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.compiler.embeddable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.compose.ui:ui-graphics:1.7.0")
    implementation("org.jetbrains.compose.ui:ui-graphics-desktop:1.7.0")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

val testKotlinStdlib2 by configurations.creating

dependencies {
    testKotlinStdlib2("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}

tasks.test {
    val stdlibJar = configurations.runtimeClasspath.get().find { it.name.startsWith("kotlin-stdlib-") && !it.name.contains("jdk") }
    if (stdlibJar != null) {
        systemProperty("kotlin.stdlib.path", stdlibJar.absolutePath)
    }
    systemProperty("kotlin.stdlib.kotlin2.path", testKotlinStdlib2.joinToString(File.pathSeparator) { it.absolutePath })
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "dev.rnett.gradle.mcp.repl.ReplWorker"
    }
}
