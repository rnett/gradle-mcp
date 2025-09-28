plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("io.ktor.plugin") version "3.3.0"
}

group = "dev.rnett.gradle-mcp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
}

dependencies {
    implementation("org.gradle:gradle-tooling-api:9.1.0")

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("commons-io:commons-io:2.20.0")

    implementation(kotlin("reflect"))
    implementation("io.github.smiley4:schema-kenerator-core:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-serialization:2.4.0")
    implementation("io.github.smiley4:schema-kenerator-jsonschema:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.7.2")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-di")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-config-yaml")

    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
}

ktor {
    development = false
}

kotlin {
    jvmToolchain(24)

    compilerOptions {
        freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property", "-Xcontext-parameters"))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(24)
}