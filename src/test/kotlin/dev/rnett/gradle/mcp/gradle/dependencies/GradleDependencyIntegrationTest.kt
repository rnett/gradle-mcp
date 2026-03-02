package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.DefaultGradleProvider
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleConfiguration
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.dependencies.model.GradleDependency
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
            initScriptProvider = DefaultInitScriptProvider(),
            buildManager = BuildManager()
        )
        service = DefaultGradleDependencyService(provider)

        // A single complex project to cover all test cases
        complexProject = testGradleProject {
            useKotlinDsl(false)
            settings(
                """
                rootProject.name = 'root'
                include 'sub-a', 'sub-b', 'sub-lib'
            """.trimIndent()
            )

            subproject(
                "sub-a", buildScript = """
                plugins { id 'java' }
                repositories { mavenCentral() }
                dependencies {
                    implementation 'org.slf4j:slf4j-api:1.7.30'
                    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.4'
                }
            """.trimIndent()
            )

            subproject(
                "sub-b", buildScript = """
                plugins { id 'org.jetbrains.kotlin.jvm' version '1.9.22' }
                repositories { mavenCentral() }
            """.trimIndent()
            )

            subproject(
                "sub-lib", buildScript = """
                plugins { id 'java-library' }
                group = 'com.example'
                version = '1.0'
                java {
                    registerFeature('feature1') { usingSourceSet(sourceSets.main) }
                    registerFeature('feature2') { usingSourceSet(sourceSets.main) }
                }
                configurations {
                    config1
                    config2
                }
                repositories { mavenCentral() }
                dependencies {
                    feature1Api 'org.slf4j:slf4j-api:1.7.30'
                    feature2Api 'com.google.guava:guava:30.1-jre'
                    config1 'org.slf4j:slf4j-api:1.7.30'
                    config2 'com.google.guava:guava:30.1-jre'
                }
            """.trimIndent()
            )

            buildScript(
                """
                plugins { id 'java' }
                repositories { mavenCentral() }
                configurations {
                    parentConf
                    childConf.extendsFrom parentConf
                }
                dependencies {
                    implementation project(':sub-a')
                    implementation project(':sub-b')
                    implementation(project(':sub-lib')) {
                        capabilities { requireCapability 'com.example:sub-lib-feature1' }
                    }
                    implementation(project(':sub-lib')) {
                        capabilities { requireCapability 'com.example:sub-lib-feature2' }
                    }
                    runtimeOnly project(path: ':sub-lib', configuration: 'config2')
                    
                    parentConf 'org.slf4j:slf4j-api:1.7.30'
                    childConf 'com.google.guava:guava:30.1-jre'
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
        val report = service.getDependencies(projectRoot, projectPath = ":sub-a")

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
        val report = service.getDependencies(projectRoot, projectPath = ":sub-b")

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
        val report = service.getDependencies(projectRoot, projectPath = ":sub-a", configuration = "testCompileClasspath")

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
        val report = service.getDependencies(projectRoot, projectPath = ":sub-a", configuration = "runtimeClasspath")

        val subA = report.projects.find { it.path == ":sub-a" }
        assertNotNull(subA)

        assertEquals(1, subA.configurations.size)
        assertEquals("runtimeClasspath", subA.configurations[0].name)
    }

    @Test
    fun `can filter only direct dependencies`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = service.getDependencies(projectRoot, projectPath = ":sub-a", configuration = "testCompileClasspath", onlyDirect = true)

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
        val report = service.getDependencies(projectRoot)

        assertNotNull(report)
        assertEquals(4, report.projects.size, "Should have root, sub-a, sub-b, and sub-lib")
    }

    @Test
    fun `can get dependencies for particular configuration path`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val config = service.getConfigurationDependencies(projectRoot, ":sub-a:implementation")

        assertNotNull(config)
        assertEquals("implementation", config.name)
    }

    @Test
    fun `can get dependencies for particular source set path`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = service.getSourceSetDependencies(projectRoot, ":sub-a:main")

        assertNotNull(report)
        assertEquals("main", report.name)
        assertTrue(report.configurations.any { it.name == "implementation" })
    }

    @Test
    fun `can check for updates`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = service.getDependencies(projectRoot, projectPath = ":sub-a", checkUpdates = true)

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
        val report = service.getDependencies(
            projectRoot,
            projectPath = ":sub-a",
            checkUpdates = true,
            versionFilter = "^(?i).+?(?<![.-](?:alpha|beta|rc|m|preview|snapshot|canary)[0-9]*)$"
        )

        val slf4j = report.projects.flatMap { it.configurations }
            .flatMap { it.dependencies }
            .find { it.name.contains("slf4j-api") }

        assertNotNull(slf4j)
        assertNotNull(slf4j.latestVersion)
        // Verify latest version is stable (doesn't contain beta etc)
        val nonStable = listOf("alpha", "beta", "rc", "m", "preview", "snapshot", "canary")
        assertTrue(nonStable.none { slf4j.latestVersion!!.contains(it, ignoreCase = true) }, "Latest version ${slf4j.latestVersion} should be stable")
    }

    // --- Tests from GradleDependencyConfigurationTest ---

    @Test
    fun `differentiates between different capabilities of the same project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(complexProject.pathString())
        val report = service.getDependencies(projectRoot, projectPath = ":")

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
        val report = service.getDependencies(projectRoot, projectPath = ":")

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
        val report = service.getDependencies(projectRoot, projectPath = ":")

        val rootProject = report.projects.find { it.path == ":" }
        assertNotNull(rootProject)

        val childConf = rootProject.configurations.find { it.name == "childConf" }
        assertNotNull(childConf)
        assertEquals(listOf("parentConf"), childConf.extendsFrom)

        val allDeps = rootProject.allDependencies("childConf")
        assertTrue(allDeps.any { it.id.contains("guava") })
        assertTrue(allDeps.any { it.id.contains("slf4j-api") })
    }
}
