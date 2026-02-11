package dev.rnett.gradle.mcp.gradle.fixtures

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

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
            val projectDir = Files.createTempDirectory("gradle-mcp-test-")
            val projectBuilder = GradleProjectBuilder(projectDir)
            projectBuilder.builder()
            projectBuilder.build()
            return GradleProjectFixture(projectDir, cleanupOnClose = true)
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
    fun gradleUserHome(): Path = projectDir.resolve("gradle-user-home")

    /**
     * Returns the absolute path as a string.
     */
    fun pathString(): String = projectDir.toAbsolutePath().toString()
}

/**
 * Builder for creating test Gradle projects.
 */
class GradleProjectBuilder(private val projectDir: Path) {
    private var gradleVersion: String = "9.3.1"
    private var settingsContent: String? = null
    private var buildScriptContent: String? = null
    private val subprojects = mutableListOf<SubprojectConfig>()
    private val files = mutableMapOf<String, String>()
    private var useKotlinDsl: Boolean = true

    data class SubprojectConfig(
        val name: String,
        val buildScript: String? = null,
        val files: Map<String, String> = emptyMap()
    )

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
     * Builds the project structure on disk.
     */
    fun build() {
        // Create gradle user home
        val gradleUserHome = projectDir.resolve("gradle-user-home")
        Files.createDirectories(gradleUserHome)

        // Create gradle wrapper
        createGradleWrapper()

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
        projectDir.resolve(settingsFile).writeText(settingsContent ?: defaultSettings)

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

        // Create additional files
        files.forEach { (path, content) ->
            val file = projectDir.resolve(path)
            Files.createDirectories(file.parent)
            file.writeText(content)
        }
    }

    private fun createGradleWrapper() {
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
                testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
                kotlin("jvm") version "2.0.0"
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                testImplementation(kotlin("test"))
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
                
                import kotlin.test.Test
                
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
