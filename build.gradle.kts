import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.power.assert)
    application
    `maven-publish`
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.shadow)
    `java-test-fixtures`
    `jvm-test-suite`
}

repositories {
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
}

application {
    mainClass.set("dev.rnett.gradle.mcp.Application")
    applicationDefaultJvmArgs = listOf(
        "-Xmx1024m",
        "-Xms256m"
    )
}

val updateToolsList by tasks.registering(JavaExec::class) {
    inputs.files(sourceSets.main.get().runtimeClasspath).withPathSensitivity(PathSensitivity.NONE)
    inputs.files(sourceSets.main.get().kotlin).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(project.rootDir.resolve("docs/tools")).withPathSensitivity(PathSensitivity.RELATIVE)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.rnett.gradle.mcp.UpdateTools")
    args = listOf(project.rootDir.resolve("docs/tools").absolutePath)
}

val verifyToolsList by tasks.registering(JavaExec::class) {
    inputs.files(sourceSets.main.get().runtimeClasspath).withPathSensitivity(PathSensitivity.NONE)
    inputs.files(sourceSets.main.get().kotlin).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(project.rootDir.resolve("docs/tools")).withPathSensitivity(PathSensitivity.RELATIVE)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.rnett.gradle.mcp.UpdateTools")
    args = listOf(project.rootDir.resolve("docs/tools").absolutePath, "--verify")
}

tasks.check {
    dependsOn(verifyToolsList)
}

val zipSkills by tasks.registering(Zip::class) {
    from("skills")
    archiveFileName.set("skills.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/resources/skills"))
}

sourceSets {
    main {
        resources {
            srcDir(zipSkills.map { it.destinationDirectory.get() })
        }
    }
}

dependencies {
    api(project(":repl-shared"))
    api(libs.gradle.tooling.api)

    api(libs.caffeine)
    api(libs.commons.io)

    api(libs.kotlin.reflect)
    api(libs.schema.kenerator.core)
    api(libs.schema.kenerator.serialization)
    api(libs.schema.kenerator.jsonschema)
    api(libs.kotlinx.serialization.json)

    api(libs.flexmark.html2md)

    api(libs.kotlinx.coroutines.core)
    api(libs.mcp.sdk)
    api(libs.jsoup)
    api(libs.koin.core)
    api(libs.koin.ktor)
    api(libs.koin.logger.slf4j)
    api(libs.ktor.server.netty)
    api(libs.ktor.server.di)
    api(libs.ktor.server.sse)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.config.yaml)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.xml)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.logback.classic)

    api(libs.lucene.core)
    api(libs.lucene.codecs)
    api(libs.lucene.analysis.common)
    api(libs.lucene.queryparser)
    api(libs.lucene.highlighter)

    api(libs.treesitter)
    api(libs.treesitter.java)
    api(libs.treesitter.kotlin)

    testFixturesApi(project)
    testFixturesApi(libs.kotlin.test)
    testFixturesApi(libs.kotlinx.coroutines.test)
    testFixturesApi(libs.junit.jupiter)
    testFixturesApi(libs.mockk)
    testFixturesApi(libs.koin.test)
    testFixturesApi(libs.koin.test.junit5)
    testFixturesApi(libs.ktor.client.cio)
    testFixturesApi(libs.ktor.client.content.negotiation)
    testFixturesApi(libs.ktor.client.mock)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-Xcontext-parameters",
            "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi"
        )
    }
}

val isCI = providers.environmentVariable("CI").orNull != null

testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation(project.dependencies.testFixtures(project()))
            }


            targets {
                all {
                    testTask.configure {
                        maxParallelForks = if (isCI) 3 else 8
                        shouldRunAfter(tasks.test)
                        maxHeapSize = "1g"
                    }
                }
            }
        }
    }
}

tasks.test {
    if (isCI) {
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
    } else {
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "10")
    }
    maxHeapSize = "2g"
}

tasks.withType<Test>().all {
    jvmArgs("--add-modules", "jdk.incubator.vector")
    useJUnitPlatform()
    systemProperty("GRADLE_MCP_LOG_DIR", layout.buildDirectory.dir("test-logs").get().asFile.absolutePath)
    systemProperty("docs.updateSnapshots", System.getProperty("docs.updateSnapshots"))
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(25)
}

buildConfig {
    sourceSets.named("testFixtures") {
        useKotlinOutput { internalVisibility = false }
        buildConfigField("KOTLIN_VERSION", libs.versions.kotlin.get())
        buildConfigField("KOTLINX_SERIALIZATION_VERSION", libs.versions.kotlinxSerializationJson.get())
        buildConfigField("SLF4J_VERSION", libs.versions.slf4j.get())
        buildConfigField("GUAVA_VERSION", libs.versions.guava.get())
        buildConfigField("JUNIT_JUPITER_VERSION", libs.versions.junit.jupiter.get())
        buildConfigField("COMPOSE_VERSION", libs.versions.jetbrains.compose.get())
        buildConfigField("GRADLE_VERSION", libs.versions.gradleToolingApi.get())
        buildConfigField("AGP_9_VERSION", libs.versions.agp9.get())
        buildConfigField("ANDROIDX_COMPOSE_VERSION", libs.versions.androidxCompose.get())
        buildConfigField("ANDROID_COMPILE_SDK", "35")
        buildConfigField("ANDROID_MIN_SDK", "24")
        buildConfigField("ANDROID_TARGET_SDK", "35")
        buildConfigField("ANDROIDX_ACTIVITY_COMPOSE_VERSION", libs.versions.androidxActivityCompose.get())
    }
    packageName("dev.rnett.gradle.mcp")
    buildConfigField("GRADLE_VERSION", libs.versions.gradleToolingApi.get())
    buildConfigField("APP_VERSION", provider { "${project.version}" })

    // need to manage manually
    buildConfigField("REPL_WORKER_JAR", "repl-worker.jar")
    buildConfigField("BUNDLED_JARS", "repl-worker.jar")
    buildConfigField("ALL_INIT_SCRIPTS", provider {
        layout.projectDirectory.dir("src/main/resources/init-scripts")
            .asFile.listFiles().map { it.name }
            .joinToString("|")
    })
// Ensure build is re-run if init scripts, skills, or bundled jars change
    project.tasks.matching { it.name == "generateBuildConfig" }.configureEach {
        inputs.dir("src/main/resources")
        inputs.dir("skills")
    }
}

tasks.processResources {
    from(project(":repl-worker").tasks.named("shadowJar")) {
        rename { "repl-worker.jar" }
    }
}

tasks.shadowJar {
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    manifest {
        attributes("Add-Modules" to "jdk.incubator.vector")
    }
}

tasks.jar {
    archiveClassifier = "single"
    manifest {
        attributes("Add-Modules" to "jdk.incubator.vector")
    }
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
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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

allprojects {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    plugins.withType<org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradlePlugin> {
        configure<PowerAssertGradleExtension> {
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
    }
}