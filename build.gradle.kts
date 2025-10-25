import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.power.assert)
    alias(libs.plugins.ktor)
    `maven-publish`
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.vanniktech.maven.publish)
}

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
}

val sharedJvmArgs = listOf(
    "-Xmx1024m",
    "-Xms256m",
//    "--enable-native-access=ALL-UNNAMED",
//    "--sun-misc-unsafe-memory-access=allow"
)

application {
    mainClass.set("dev.rnett.gradle.mcp.Application")
    applicationDefaultJvmArgs = sharedJvmArgs
}

val updateToolsList by tasks.registering(JavaExec::class) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.rnett.gradle.mcp.UpdateTools")
    args = listOf(project.rootDir.resolve("docs/tools").absolutePath)
}

val verifyToolsList by tasks.registering(JavaExec::class) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.rnett.gradle.mcp.UpdateTools")
    args = listOf(project.rootDir.resolve("docs/tools").absolutePath, "--verify")
}

tasks.check {
    dependsOn(verifyToolsList)
}

dependencies {
    implementation(libs.gradle.tooling.api)

    implementation(libs.caffeine)
    implementation(libs.commons.io)

    implementation(libs.kotlin.reflect)
    implementation(libs.schema.kenerator.core)
    implementation(libs.schema.kenerator.serialization)
    implementation(libs.schema.kenerator.jsonschema)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mcp.sdk)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.config.yaml)

    implementation(libs.logback.classic)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
}

ktor {
    development = false
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-Xcontext-parameters"
        )
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert",
        // kotlin.test
        "kotlin.test.assertTrue",
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotEquals",
        "kotlin.test.assertNull",
        "kotlin.test.assertNotNull",
        "kotlin.test.assertFails",
        "kotlin.test.assertFailsWith",
        "kotlin.test.assertContains",
        "kotlin.test.assertContentEquals",
        "kotlin.test.assertIs",
        "kotlin.test.assertIsNot",
        "kotlin.test.assertSame",
        "kotlin.test.assertNotSame",
    )
}

tasks.test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(24)
}

buildConfig {
    packageName("dev.rnett.gradle.mcp")
    buildConfigField("APP_VERSION", provider { "${project.version}" })
}

tasks.shadowJar {
    archiveClassifier = ""
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    coordinates(group.toString(), project.name, project.version.toString())

    signAllPublications()

    pom {
        name.set("Gradle MCP")
        description.set("A MCP server for Gradle.")
        inceptionYear.set("2025")
        url.set("https://gradle-mcp.rnett.dev/")
        this.licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("rnett")
                name.set("Ryan Nett")
                url.set("https://github.com/rnett/")
            }
        }
        scm {
            url.set("https://github.com/rnett/gradle-mcp/")
            connection.set("scm:git:git://github.com/rnett/gradle-mcp.git")
            developerConnection.set("scm:git:ssh://git@github.com/rnett/gradle-mcp.git")
        }
    }
}
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        register<MavenPublication>("shadow") {
            from(components["shadow"])
            artifact(tasks.kotlinSourcesJar)
            artifact(tasks.named("javadocJar"))
        }
    }
}