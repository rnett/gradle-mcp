package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.fixtures.GradleProjectFixture
import dev.rnett.gradle.mcp.gradle.fixtures.testJavaProject
import dev.rnett.gradle.mcp.gradle.fixtures.testKotlinProject
import dev.rnett.gradle.mcp.gradle.model.GradleDependency
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
    private lateinit var javaProject: GradleProjectFixture
    private lateinit var kotlinProject: GradleProjectFixture

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
        javaProject = testJavaProject()
        kotlinProject = testKotlinProject()
    }

    @AfterAll
    fun cleanupAll() {
        provider.close()
        javaProject.close()
        kotlinProject.close()
    }

    @Test
    fun `can get dependencies for java project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val report = service.getDependencies(projectRoot, null, null, allProjects = true)

        assertNotNull(report)
        val rootProject = report.projects.find { it.path == "test-project" }
        assertNotNull(rootProject)

        // Check repositories
        assertTrue(rootProject.repositories.any { it.name == "MavenRepo" || it.url?.contains("repo.maven.apache.org") == true }, "Should have Maven Central")

        // Check source sets
        val mainSourceSet = rootProject.sourceSets.find { it.name == "main" }
        assertNotNull(mainSourceSet)
        assertTrue(mainSourceSet.configurations.contains("implementation"))

        // Check configurations
        val implementation = rootProject.configurations.find { it.name == "implementation" }
        assertNotNull(implementation)
    }

    @Test
    fun `can get dependencies for kotlin project`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(kotlinProject.pathString())
        val report = service.getDependencies(projectRoot, null, null, allProjects = true)

        assertNotNull(report)
        val rootProject = report.projects.find { it.path == "test-project" }
        assertNotNull(rootProject)

        // Kotlin projects should have kotlin-stdlib in compileClasspath
        val compileClasspath = rootProject.configurations.find { it.name == "compileClasspath" }
        assertNotNull(compileClasspath)

        fun hasKotlinStdlib(deps: List<GradleDependency>): Boolean {
            return deps.any { it.id.contains("kotlin-stdlib") || hasKotlinStdlib(it.children) }
        }

        assertTrue(hasKotlinStdlib(compileClasspath.dependencies), "Should contain kotlin-stdlib in compileClasspath (direct or transitive)")
    }

    @Test
    fun `can get transitive dependencies`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        // JUnit Jupiter is a good candidate for transitive deps
        val report = service.getDependencies(projectRoot, "testCompileClasspath", null, allProjects = true)

        val rootProject = report.projects.find { it.path == "test-project" }
        assertNotNull(rootProject)

        val testCompileClasspath = rootProject.configurations.find { it.name == "testCompileClasspath" }
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
        assertNotNull(jupiter, "Should find junit-jupiter in testCompileClasspath (possibly transitive)")
        // Since it's in the BOM or brought in by other things, it might have children
        // The logs showed: 
        // DEP: test-project | ** | org.junit.jupiter:junit-jupiter:5.11.4 | org.junit.jupiter | junit-jupiter | 5.11.4 |  | false
        // So it's a child of the project (root project :) which is level 1 (*). So jupiter is level 2 (**).
    }

    @Test
    fun `can filter dependencies by configuration`() = runTest(timeout = 180.seconds) {
        val projectRoot = GradleProjectRoot(javaProject.pathString())
        val report = service.getDependencies(projectRoot, "runtimeClasspath", null, allProjects = true)

        val rootProject = report.projects.find { it.path == "test-project" }
        assertNotNull(rootProject)

        // Should only have runtimeClasspath configuration
        assertEquals(1, rootProject.configurations.size)
        assertEquals("runtimeClasspath", rootProject.configurations[0].name)
    }
}
