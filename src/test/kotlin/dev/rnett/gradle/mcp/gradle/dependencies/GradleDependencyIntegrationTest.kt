package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.BuildConfig
import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture
import dev.rnett.gradle.mcp.gradle.fixtures.testGradleProject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleDependencyIntegrationTest {

    private lateinit var provider: DefaultGradleProvider
    private lateinit var service: GradleDependencyService
    private lateinit var complexProject: GradleProjectFixture

    @BeforeAll
    fun setupAll() {
        provider = DefaultGradleProvider(
            config = GradleConfiguration(
                maxConnections = 4,
                ttl = 10.seconds,
                allowPublicScansPublishing = false
            ),
            buildManager = BuildManager()
        )
        service = DefaultGradleDependencyService(provider)

        // A single complex project to cover all test cases
        complexProject = testGradleProject {
            useKotlinDsl(true)
            settings(
                """
                rootProject.name = "root"
                include("sub-a", "sub-b", "sub-lib", "sub-kmp")
            """.trimIndent()
            )

            subproject(
                "sub-a", buildScript = """
                plugins { id("java") }
                repositories { mavenCentral() }
                dependencies {
                    implementation("org.slf4j:slf4j-api:${BuildConfig.SLF4J_VERSION}")
                    testImplementation("org.junit.jupiter:junit-jupiter:${BuildConfig.JUNIT_JUPITER_VERSION}")
                }
            """.trimIndent()
            )

            subproject(
                "sub-b", buildScript = """
                plugins { id("org.jetbrains.kotlin.jvm") version "${BuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
            """.trimIndent()
            )

            subproject(
                "sub-lib", buildScript = """
                plugins { id("java-library") }
                group = "com.example"
                version = "1.0"
                java {
                    registerFeature("feature1") { usingSourceSet(sourceSets.main.get()) }
                    registerFeature("feature2") { usingSourceSet(sourceSets.main.get()) }
                }
                configurations {
                    create("config1")
                    create("config2")
                }
                repositories { mavenCentral() }
                dependencies {
                    "feature1Api"("org.slf4j:slf4j-api:${BuildConfig.SLF4J_VERSION}")
                    "feature2Api"("com.google.guava:guava:${BuildConfig.GUAVA_VERSION}")
                    "config1"("org.slf4j:slf4j-api:${BuildConfig.SLF4J_VERSION}")
                    "config2"("com.google.guava:guava:${BuildConfig.GUAVA_VERSION}")
                }
            """.trimIndent()
            )

            subproject(
                "sub-kmp", buildScript = """
                plugins { id("org.jetbrains.kotlin.multiplatform") version "${BuildConfig.KOTLIN_VERSION}" }
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    linuxX64()
                    sourceSets {
                        commonMain {
                            dependencies {
                                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${BuildConfig.KOTLINX_SERIALIZATION_VERSION}")
                            }
                        }
                    }
                }
            """.trimIndent()
            )

            buildScript(
                """
                buildscript {
                    repositories { mavenCentral() }
                    dependencies {
                        classpath("org.slf4j:slf4j-api:${BuildConfig.SLF4J_VERSION}")
                    }
                }
                plugins { id("java") }
                repositories { mavenCentral() }
                configurations {
                    val parentConf by creating
                    val childConf by creating { extendsFrom(parentConf) }
                }
                dependencies {
                    implementation(project(":sub-a"))
                    implementation(project(":sub-b"))
                    implementation(project(":sub-lib")) {
                        capabilities { requireCapability("com.example:sub-lib-feature1") }
                    }
                    implementation(project(":sub-lib")) {
                        capabilities { requireCapability("com.example:sub-lib-feature2") }
                    }
                    runtimeOnly(project(path = ":sub-lib", configuration = "config2"))
                    
                    "parentConf"("org.slf4j:slf4j-api:${BuildConfig.SLF4J_VERSION}")
                    "childConf"("com.google.guava:guava:${BuildConfig.GUAVA_VERSION}")
                }
            """.trimIndent()
            )
        }
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        complexProject.close()
    }

    @Test
    fun `can get dependencies for java project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-a") }

        assertNotNull(report)
        val subA = report.projects.find { it.path == ":sub-a" }
        assertNotNull(subA)

        // Check repositories
        assertTrue(subA.repositories.any { it.url?.contains("repo.maven.apache.org") == true }, "Should have Maven Central")

        // Check source sets
        val mainSourceSet = subA.sourceSets.find { it.name == "main" }
        assertNotNull(mainSourceSet)
        assertTrue(mainSourceSet.configurations.contains("implementation"))
    }

    @Test
    fun `can get dependencies for kotlin project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-b") }

        assertNotNull(report)
        val subB = report.projects.find { it.path == ":sub-b" }
        assertNotNull(subB)

        val compileClasspath = subB.configurations.find { it.name == "compileClasspath" }
        assertNotNull(compileClasspath)

        fun hasKotlinStdlib(deps: List<GradleDependency>): Boolean {
            return deps.any { it.id.contains("kotlin-stdlib") || hasKotlinStdlib(it.children) }
        }

        assertTrue(hasKotlinStdlib(compileClasspath.dependencies), "Should contain kotlin-stdlib in compileClasspath")
    }

    @Test
    fun `can get transitive dependencies`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-a", configuration = "testCompileClasspath") }

        val subA = report.projects.find { it.path == ":sub-a" }
        assertNotNull(subA)

        val testCompileClasspath = subA.configurations.find { it.name == "testCompileClasspath" }
        assertNotNull(testCompileClasspath)

        fun findInDeps(deps: List<GradleDependency>, idPart: String): GradleDependency? {
            for (dep in deps) {
                if (dep.id.contains(idPart)) return dep
                val found = findInDeps(dep.children, idPart)
                if (found != null) return found
            }
            return null
        }

        val jupiter = findInDeps(testCompileClasspath.dependencies, "junit-jupiter")
        assertNotNull(jupiter, "Should find junit-jupiter in testCompileClasspath")
    }

    @Test
    fun `can filter dependencies by configuration`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-a", configuration = "runtimeClasspath") }

        val subA = report.projects.find { it.path == ":sub-a" }
        assertNotNull(subA)

        assertEquals(1, subA.configurations.size)
        assertEquals("runtimeClasspath", subA.configurations[0].name)
    }

    @Test
    fun `can filter only direct dependencies`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-a", configuration = "testCompileClasspath", onlyDirect = true) }

        val subA = report.projects.find { it.path == ":sub-a" }
        assertNotNull(subA)

        val testCompileClasspath = subA.configurations.find { it.name == "testCompileClasspath" }
        assertNotNull(testCompileClasspath)

        assertTrue(testCompileClasspath.dependencies.any { it.name == "junit-jupiter" }, "Should contain direct dependency junit-jupiter")
        assertTrue(testCompileClasspath.dependencies.all { it.children.isEmpty() }, "Direct only mode should have no children")
    }

    @Test
    fun `can get dependencies for all projects`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot) }

        assertNotNull(report)
        assertEquals(5, report.projects.size, "Should have root, sub-a, sub-b, sub-lib, and sub-kmp")
    }

    @Test
    fun `can get dependencies for kmp project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-kmp") }

        assertNotNull(report)
        val subKmp = report.projects.find { it.path == ":sub-kmp" }
        assertNotNull(subKmp)

        // Check for JVM and Linux targets (source sets and configurations)
        val jvmMain = subKmp.sourceSets.find { it.name == "jvmMain" }
        assertNotNull(jvmMain)
        assertTrue(jvmMain.configurations.contains("jvmMainImplementation"))

        val linuxMain = subKmp.sourceSets.find { it.name == "kotlin:linuxX64Main" }
        assertNotNull(linuxMain, "Should find linuxX64Main source set (prefixed with kotlin: in KMP)")

        // Verify artifacts and variants for a common dependency
        val commonMain = subKmp.sourceSets.find { it.name == "kotlin:commonMain" }
        assertNotNull(commonMain)

        val jvmCompileClasspath = subKmp.configurations.find { it.name == "jvmCompileClasspath" }
        assertNotNull(jvmCompileClasspath)

        val serialization = jvmCompileClasspath.dependencies.find { it.id.contains("kotlinx-serialization-json") }
        assertNotNull(serialization)

        // Check that variant information is present
        assertNotNull(serialization.variant, "Variant should not be null for KMP dependency")
        assertTrue(serialization.variant.contains("jvmApiElements") || serialization.variant.contains("jvmRuntimeElements"), "Variant should be a JVM variant, got ${serialization.variant}")

        assertNotNull(serialization.version)
    }

    @Test
    fun `can get dependencies for particular configuration path`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val config = with(ProgressReporter.NONE) { service.getConfigurationDependencies(projectRoot, ":sub-a:implementation") }

        assertNotNull(config)
        assertEquals("implementation", config.name)
    }

    @Test
    fun `can get dependencies for particular source set path`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getSourceSetDependencies(projectRoot, ":sub-a:main") }

        assertNotNull(report)
        assertEquals("main", report.name)
        assertTrue(report.configurations.any { it.name == "implementation" })
    }

    @Test
    fun `can check for updates`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":sub-a", checkUpdates = true) }

        val slf4j = report.projects.flatMap { it.configurations }
            .flatMap { it.dependencies }
            .find { it.name.contains("slf4j-api") }

        assertNotNull(slf4j)
        assertNotNull(slf4j.latestVersion, "latestVersion should not be null when checkUpdates is true")
        assertTrue(slf4j.latestVersion != slf4j.version, "latestVersion should be different from current version")
    }

    @Test
    fun `can filter stable version only`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        // slf4j-api 1.7.30 is old, should have many stable updates.
        // We'll use a dummy dependency that has a beta update if possible, but for now we'll just check if it works with the regex.
        // Since we can't easily control the external repo, we'll just verify the call doesn't fail.
        val report = with(ProgressReporter.NONE) {
            service.getDependencies(
                projectRoot,
                projectPath = ":sub-a",
                checkUpdates = true,
                versionFilter = "^(?i).+?(?<![.-](?:alpha|beta|rc|m|preview|snapshot|canary)[0-9]*)$"
            )
        }

        val slf4j = report.projects.flatMap { it.configurations }
            .flatMap { it.dependencies }
            .find { it.name.contains("slf4j-api") }

        assertNotNull(slf4j)
        assertNotNull(slf4j.latestVersion)
        // Verify latest version is stable (doesn't contain beta etc)
        val nonStable = listOf("alpha", "beta", "rc", "m", "preview", "snapshot", "canary")
        assertTrue(nonStable.none { slf4j.latestVersion.contains(it, ignoreCase = true) }, "Latest version ${slf4j.latestVersion} should be stable")
    }

    // --- Tests from GradleDependencyConfigurationTest ---

    @Test
    fun `differentiates between different capabilities of the same project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":") }

        val rootProject = report.projects.find { it.path == ":" }
        assertNotNull(rootProject)

        val subDeps = rootProject.allDependencies("compileClasspath").filter { it.id.contains(":sub-lib") }
        assertEquals(2, subDeps.size, "Should have 2 separate entries for :sub-lib because they have different capabilities")

        assertTrue(subDeps.any { it.children.any { c -> c.id.contains("slf4j-api") } }, "One :sub-lib should have slf4j-api")
        assertTrue(subDeps.any { it.children.any { c -> c.id.contains("guava") } }, "One :sub-lib should have guava")
    }

    @Test
    fun `differentiates between different configurations of the same project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":") }

        val rootProject = report.projects.find { it.path == ":" }
        assertNotNull(rootProject)

        val subDeps = rootProject.allDependencies("runtimeClasspath").filter { it.id.contains(":sub-lib") }

        assertTrue(subDeps.size >= 2, "Should have multiple entries for :sub-lib")
        assertTrue(subDeps.any { it.children.any { c -> c.id.contains("guava") } }, "Should find guava child (from feature2 or config2)")
        assertTrue(subDeps.any { it.children.any { c -> c.id.contains("slf4j-api") } }, "Should find slf4j child (from feature1)")
    }

    @Test
    fun `reports extendsFrom correctly`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":") }

        val rootProject = report.projects.find { it.path == ":" }
        assertNotNull(rootProject)

        val childConf = rootProject.configurations.find { it.name == "childConf" }
        assertNotNull(childConf)
        assertEquals(listOf("parentConf"), childConf.extendsFrom)

        val allDeps = rootProject.allDependencies("childConf")
        assertTrue(allDeps.any { it.id.contains("guava") })
        assertTrue(allDeps.any { it.id.contains("slf4j-api") })
    }

    @Test
    fun `can get buildscript dependencies`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = with(ProgressReporter.NONE) { service.getDependencies(projectRoot, projectPath = ":") }

        val rootProject = report.projects.find { it.path == ":" }
        assertNotNull(rootProject)

        val buildscriptClasspath = rootProject.configurations.find { it.name == "buildscript:classpath" }
        assertNotNull(buildscriptClasspath, "Should find buildscript:classpath configuration")

        // buildscript configurations should be resolvable
        assertTrue(buildscriptClasspath.isResolvable, "buildscript:classpath should be resolvable")
    }

    @Test
    fun `can get dependencies and sources for normally applied plugins`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val config = with(ProgressReporter.NONE) { service.downloadConfigurationSources(projectRoot, ":sub-b:buildscript:classpath") }

        assertNotNull(config)
        assertEquals("buildscript:classpath", config.name)
        assertTrue(config.dependencies.isNotEmpty(), "Should have buildscript dependencies")

        val allDeps = config.allDependencies().toList()

        val kotlinPlugin = allDeps.find { it.name.contains("kotlin-gradle-plugin") }
        assertNotNull(kotlinPlugin, "Should find kotlin-gradle-plugin")

        // This fails locally if downloading sources isn't working/mocked perfectly in tests, but it tests the feature flow
        // The fact that we even get the dependency is the primary win.
        assertNotNull(kotlinPlugin.sourcesFile, "Should find sources for kotlin-gradle-plugin")
    }
}
