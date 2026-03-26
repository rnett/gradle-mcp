package dev.rnett.gradle.mcp.fixtures.gradle

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.TestFixturesBuildConfig
import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/** JVM heap size used for all test Gradle daemons. */
private const val TEST_DAEMON_HEAP = "256m"

/**
 * A fixture for creating temporary Gradle projects for testing.
 * Projects are created in a temporary directory and cleaned up after use.
 */
class GradleProjectFixture private constructor(
    val projectDir: Path,
    private val cleanupOnClose: Boolean = true
) : AutoCloseable {

    companion object {
        /**
         * Creates a new temporary Gradle project with the given configuration.
         */
        fun create(
            builder: GradleProjectBuilder.() -> Unit = {}
        ): GradleProjectFixture {
            val projectBuilder = GradleProjectBuilder()
            projectBuilder.builder()

            val hash = projectBuilder.computeHash()
            val projectDir = SharedTestInfrastructure.sharedWorkingDir.resolve("projects").resolve(hash)

            if (!projectDir.exists()) {
                projectDir.createDirectories()
                projectBuilder.build(projectDir)
            }

            return GradleProjectFixture(projectDir, cleanupOnClose = false)
        }
    }

    override fun close() {
        if (cleanupOnClose) {
            projectDir.toFile().deleteRecursively()
        }
    }

    /**
     * Returns the absolute path to the project directory.
     */
    fun path(): Path = projectDir

    /**
     * Returns the absolute path to the gradle user home.
     */
    fun gradleUserHome(): Path = SharedTestInfrastructure.sharedWorkingDir.resolve("gradle-user-home").apply { createDirectories() }

    /**
     * Returns the absolute path as a string.
     */
    fun pathString(): String = projectDir.toAbsolutePath().toString()
}

/**
 * Builder for creating test Gradle projects.
 */
class GradleProjectBuilder {
    private var gradleVersion: String = BuildConfig.GRADLE_VERSION
    private var settingsContent: String? = null
    private var buildScriptContent: String? = null
    private val subprojects = mutableListOf<SubprojectConfig>()
    private val files = mutableMapOf<String, String>()
    private var useKotlinDsl: Boolean = true
    private val includedBuilds = mutableListOf<IncludedBuildConfig>()

    data class SubprojectConfig(
        val name: String,
        val buildScript: String? = null,
        val files: Map<String, String> = emptyMap()
    )

    data class IncludedBuildConfig(
        val dir: String,
        val settingsContent: String,
        val buildScript: String? = null
    )

    fun computeHash(): String {
        val md = MessageDigest.getInstance("SHA-256")
        fun String.update() = md.update(this.toByteArray())

        "gradleVersion=$gradleVersion\n".update()
        "useKotlinDsl=$useKotlinDsl\n".update()
        "settingsContent=${settingsContent ?: "null"}\n".update()
        "buildScriptContent=${buildScriptContent ?: "null"}\n".update()

        subprojects.sortedBy { it.name }.forEach { sp ->
            "subproject=${sp.name}\n".update()
            "subproject.buildScript=${sp.buildScript ?: "null"}\n".update()
            sp.files.toSortedMap().forEach { (k, v) ->
                "subproject.file=$k:$v\n".update()
            }
        }

        includedBuilds.sortedBy { it.dir }.forEach { ib ->
            "includedBuild=${ib.dir}\n".update()
            "includedBuild.settings=${ib.settingsContent}\n".update()
            "includedBuild.buildScript=${ib.buildScript ?: "null"}\n".update()
        }

        files.toSortedMap().forEach { (k, v) ->
            "file=$k:$v\n".update()
        }

        return md.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Sets the Gradle version for the project.
     */
    fun gradleVersion(version: String) {
        this.gradleVersion = version
    }

    /**
     * Sets whether to use Kotlin DSL (default: true) or Groovy DSL.
     */
    fun useKotlinDsl(use: Boolean = true) {
        this.useKotlinDsl = use
    }

    /**
     * Sets the settings.gradle(.kts) content.
     */
    fun settings(content: String) {
        this.settingsContent = content
    }

    /**
     * Sets the build.gradle(.kts) content for the root project.
     */
    fun buildScript(content: String) {
        this.buildScriptContent = content
    }

    /**
     * Adds a subproject with optional build script and files.
     */
    fun subproject(name: String, buildScript: String? = null, files: Map<String, String> = emptyMap()) {
        subprojects.add(SubprojectConfig(name, buildScript, files))
    }

    /**
     * Adds a file to the project with the given path relative to project root.
     */
    fun file(path: String, content: String) {
        files[path] = content
    }

    /**
     * Adds an included build (composite build) under the given subdirectory.
     * Appends `includeBuild("<dir>")` to the root settings file.
     */
    fun includeBuild(dir: String, builder: IncludedBuildBuilder.() -> Unit) {
        val b = IncludedBuildBuilder(dir)
        b.builder()
        includedBuilds.add(b.build())
    }

    class IncludedBuildBuilder(private val dir: String) {
        private var settingsContent: String = "rootProject.name = \"$dir\""
        private var buildScript: String? = null

        fun settings(content: String) {
            settingsContent = content
        }

        fun buildScript(content: String) {
            buildScript = content
        }

        fun build() = IncludedBuildConfig(dir, settingsContent, buildScript)
    }

    /**
     * Builds the project structure on disk.
     */
    fun build(projectDir: Path) {
        // Create gradle user home and write gradle.properties to control daemon JVM args,
        // preventing the developer's real ~/.gradle/gradle.properties from affecting tests.
        val gradleUserHome = SharedTestInfrastructure.sharedWorkingDir.resolve("gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val gradleUserHomeProps = gradleUserHome.resolve("gradle.properties")
        if (!gradleUserHomeProps.toFile().exists()) {
            gradleUserHomeProps.writeText("org.gradle.jvmargs=-Xmx$TEST_DAEMON_HEAP\n")
        }

        // Create gradle wrapper
        createGradleWrapper(projectDir)

        // Create settings file
        val settingsFile = if (useKotlinDsl) "settings.gradle.kts" else "settings.gradle"
        val defaultSettings = if (subprojects.isEmpty()) {
            "rootProject.name = \"test-project\""
        } else {
            buildString {
                appendLine("rootProject.name = \"test-project\"")
                subprojects.forEach { subproject ->
                    appendLine("include(\"${subproject.name}\")")
                }
            }
        }
        val baseSettings = settingsContent ?: defaultSettings
        val finalSettings = if (includedBuilds.isEmpty()) {
            baseSettings
        } else {
            buildString {
                append(baseSettings)
                appendLine()
                includedBuilds.forEach { ib ->
                    appendLine("includeBuild(\"${ib.dir}\")")
                }
            }
        }
        projectDir.resolve(settingsFile).writeText(finalSettings)

        // Create root build script if provided
        if (buildScriptContent != null) {
            val buildFile = if (useKotlinDsl) "build.gradle.kts" else "build.gradle"
            projectDir.resolve(buildFile).writeText(buildScriptContent!!)
        }

        // Create subprojects
        subprojects.forEach { subproject ->
            val subprojectDir = projectDir.resolve(subproject.name)
            Files.createDirectories(subprojectDir)

            if (subproject.buildScript != null) {
                val buildFile = if (useKotlinDsl) "build.gradle.kts" else "build.gradle"
                subprojectDir.resolve(buildFile).writeText(subproject.buildScript)
            }

            subproject.files.forEach { (path, content) ->
                val file = subprojectDir.resolve(path)
                Files.createDirectories(file.parent)
                file.writeText(content)
            }
        }

        // Ensure gradle.properties in the project directory sets daemon JVM args.
        // Inject org.gradle.jvmargs if not already present in user-specified content.
        val gradlePropsKey = "gradle.properties"
        if (gradlePropsKey !in files) {
            files[gradlePropsKey] = "org.gradle.jvmargs=-Xmx$TEST_DAEMON_HEAP\n"
        } else if (!files.getValue(gradlePropsKey).contains("org.gradle.jvmargs")) {
            files[gradlePropsKey] = "org.gradle.jvmargs=-Xmx$TEST_DAEMON_HEAP\n${files.getValue(gradlePropsKey)}"
        }

        // Create additional files
        files.forEach { (path, content) ->
            val file = projectDir.resolve(path)
            Files.createDirectories(file.parent)
            file.writeText(content)
        }

        // Create included builds
        includedBuilds.forEach { ib ->
            val ibDir = projectDir.resolve(ib.dir)
            Files.createDirectories(ibDir)
            ibDir.resolve("settings.gradle.kts").writeText(ib.settingsContent)
            if (ib.buildScript != null) {
                ibDir.resolve("build.gradle.kts").writeText(ib.buildScript)
            }
        }
    }

    private fun createGradleWrapper(projectDir: Path) {
        // Create gradle wrapper directory
        val wrapperDir = projectDir.resolve("gradle").resolve("wrapper")
        Files.createDirectories(wrapperDir)

        // Create gradle-wrapper.properties
        val wrapperProperties = wrapperDir.resolve("gradle-wrapper.properties")
        wrapperProperties.writeText(
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent()
        )

        // Create gradlew script (Unix)
        val gradlew = projectDir.resolve("gradlew")
        gradlew.writeText(
            """
            #!/bin/sh
            exec gradle "${'$'}@"
        """.trimIndent()
        )
        gradlew.toFile().setExecutable(true)

        // Create gradlew.bat script (Windows)
        val gradlewBat = projectDir.resolve("gradlew.bat")
        gradlewBat.writeText(
            """
            @echo off
            gradle %*
        """.trimIndent()
        )
    }
}

/**
 * Helper function to create a simple Gradle project for testing.
 */
fun testGradleProject(builder: GradleProjectBuilder.() -> Unit = {}): GradleProjectFixture {
    return GradleProjectFixture.create(builder)
}

/**
 * Helper function to create a Java project with a simple structure.
 */
fun testJavaProject(
    hasTests: Boolean = true,
    additionalConfig: GradleProjectBuilder.() -> Unit = {}
): GradleProjectFixture {
    return testGradleProject {
        buildScript(
            """
            plugins {
                java
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:${TestFixturesBuildConfig.JUNIT_JUPITER_VERSION}")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }
            
            tasks.test {
                useJUnitPlatform()
            }
        """.trimIndent()
        )

        // Add a simple Java source file
        file(
            "src/main/java/com/example/Hello.java", """
            package com.example;
            
            public class Hello {
                public static String greet(String name) {
                    return "Hello, " + name + "!";
                }
            }
        """.trimIndent()
        )

        if (hasTests) {
            file(
                "src/test/java/com/example/HelloTest.java", """
                package com.example;
                
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.*;
                
                class HelloTest {
                    @Test
                    void testGreet() {
                        assertEquals("Hello, World!", Hello.greet("World"));
                    }
                }
            """.trimIndent()
            )
        }

        additionalConfig()
    }
}

/**
 * Helper function to create a Kotlin project with a simple structure.
 */
fun testKotlinProject(
    hasTests: Boolean = true,
    additionalConfig: GradleProjectBuilder.() -> Unit = {}
): GradleProjectFixture {
    return testGradleProject {
        buildScript(
            """
            plugins {
                kotlin("jvm") version "${TestFixturesBuildConfig.KOTLIN_VERSION}"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }
            
            tasks.test {
                useJUnitPlatform()
            }
        """.trimIndent()
        )

        // Add a simple Kotlin source file
        file(
            "src/main/kotlin/com/example/Hello.kt", """
            package com.example
            
            fun greet(name: String): String {
                return "Hello, ${'$'}name!"
            }
        """.trimIndent()
        )

        if (hasTests) {
            file(
                "src/test/kotlin/com/example/HelloTest.kt", """
                package com.example
                
                import org.junit.jupiter.api.Test
                
                class HelloTest {
                    @Test
                    fun `test greet`() {
                        assert(greet("World") == "Hello, World!")
                    }
                }
            """.trimIndent()
            )
        }

        additionalConfig()
    }
}

/**
 * Helper function to create a multi-project build.
 */
fun testMultiProjectBuild(
    subprojectNames: List<String> = listOf("subproject-a", "subproject-b"),
    additionalConfig: GradleProjectBuilder.() -> Unit = {}
): GradleProjectFixture {
    return testGradleProject {
        buildScript(
            """
            plugins {
                java
            }
            
            allprojects {
                repositories {
                    mavenCentral()
                }
            }
        """.trimIndent()
        )

        subprojectNames.forEach { name ->
            subproject(
                name = name,
                buildScript = """
                    plugins {
                        java
                    }
                """.trimIndent()
            )
        }

        additionalConfig()
    }
}
