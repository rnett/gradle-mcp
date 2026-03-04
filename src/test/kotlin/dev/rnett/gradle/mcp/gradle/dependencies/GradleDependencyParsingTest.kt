package dev.rnett.gradle.mcp.gradle.dependencies

import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.GradleScanTosAcceptRequest
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.Model
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GradleDependencyParsingTest {

    @Test
    fun `can parse structured output`() {
        val output = """
            PROJECT: : | project ':'
            REPOSITORY: : | MavenRepo | https://repo.maven.apache.org/maven2/
            SOURCESET: : | main | implementation,runtimeOnly,compileClasspath,runtimeClasspath
            CONFIGURATION: : | implementation | Implementation only dependencies for source set 'main'. | true
            DEP: : | * | project : | project | : | | | | false
            DEP: : | ** | org.jetbrains.kotlin:kotlin-stdlib:1.9.22 | org.jetbrains.kotlin | kotlin-stdlib | 1.9.22 | | | false
            DEP: : | *** | org.jetbrains:annotations:13.0 | org.jetbrains | annotations | 13.0 | | | false
            CONFIGURATION: : | testImplementation | Implementation only dependencies for source set 'test'. | true
            DEP: : | * | project : | project | : | | | | false
            DEP: : | ** | org.junit.jupiter:junit-jupiter:5.10.1 | org.junit.jupiter | junit-jupiter | 5.10.1 | | | false
            DEP: : | *** | org.junit.jupiter:junit-jupiter-api:5.10.1 | org.junit.jupiter | junit-jupiter-api | 5.10.1 | | | false
            DEP: : | *** | org.junit.jupiter:junit-jupiter-params:5.10.1 | org.junit.jupiter | junit-jupiter-params | 5.10.1 | | | false
            DEP: : | *** | org.junit.jupiter:junit-jupiter-engine:5.10.1 | org.junit.jupiter | junit-jupiter-engine | 5.10.1 | | | false
            DEP: : | **** | org.junit.platform:junit-platform-engine:1.10.1 | org.junit.platform | junit-platform-engine | 1.10.1 | | | false
        """.trimIndent()

        val service = DefaultGradleDependencyService(MockGradleProvider())
        val report = service.parseStructuredOutput(output)

        assertNotNull(report)
        assertEquals(1, report.projects.size)
        val project = report.projects[0]
        assertEquals(":", project.path)
        assertEquals(1, project.repositories.size)
        assertEquals("MavenRepo", project.repositories[0].name)
        assertEquals(1, project.sourceSets.size)
        assertEquals("main", project.sourceSets[0].name)
        assertEquals(2, project.configurations.size)

        val implementation = project.configurations.find { it.name == "implementation" }
        assertNotNull(implementation)
        assertEquals(1, implementation.dependencies.size)
        val projectDep = implementation.dependencies[0]
        assertEquals("project :", projectDep.id)
        assertEquals(1, projectDep.children.size)
        val stdlib = projectDep.children[0]
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.22", stdlib.id)
        assertEquals(1, stdlib.children.size)
        assertEquals("org.jetbrains:annotations:13.0", stdlib.children[0].id)
    }

    @Test
    fun `can parse already visited dependencies`() {
        val output = """
            PROJECT: : | project ':'
            CONFIGURATION: : | implementation | Implementation only dependencies for source set 'main'. | true
            DEP: : | * | project : | project | : | | | | false
            DEP: : | ** | org.jetbrains.kotlin:kotlin-stdlib:1.9.22 | org.jetbrains.kotlin | kotlin-stdlib | 1.9.22 | | | false
            DEP: : | *** | org.jetbrains:annotations:13.0 | org.jetbrains | annotations | 13.0 | | | false
            DEP: : | ** | org.jetbrains:annotations:13.0 | org.jetbrains | annotations | 13.0 | | | false
        """.trimIndent()

        val service = DefaultGradleDependencyService(MockGradleProvider())
        val report = service.parseStructuredOutput(output)

        val project = report.projects[0]
        val implementation = project.configurations[0]
        val projectDep = implementation.dependencies[0]
        assertEquals(2, projectDep.children.size)

        val firstAnnotations = projectDep.children[0].children[0]
        val secondAnnotations = projectDep.children[1]

        assertEquals("org.jetbrains:annotations:13.0", firstAnnotations.id)
        assertEquals("org.jetbrains:annotations:13.0", secondAnnotations.id)
        // secondAnnotations should now have children from knownChildren
        assertEquals(0, secondAnnotations.children.size) // knownChildren for annotations:13.0 is empty as it had no children
    }

    @Test
    fun `can parse already visited dependencies with children`() {
        val output = """
            PROJECT: : | project ':'
            CONFIGURATION: : | implementation | Implementation only dependencies for source set 'main'. | true
            DEP: : | * | A | group | A | 1.0 | | | false
            DEP: : | ** | B | group | B | 1.0 | | | false
            DEP: : | *** | C | group | C | 1.0 | | | false
            DEP: : | * | D | group | D | 1.0 | | | false
            DEP: : | ** | B | group | B | 1.0 | | | false
        """.trimIndent()

        val service = DefaultGradleDependencyService(MockGradleProvider())
        val report = service.parseStructuredOutput(output)

        val project = report.projects[0]
        val implementation = project.configurations[0]
        assertEquals(2, implementation.dependencies.size)

        val a = implementation.dependencies[0]
        val d = implementation.dependencies[1]

        assertEquals("A", a.id)
        assertEquals("D", d.id)

        val b1 = a.children[0]
        val b2 = d.children[0]

        assertEquals("B", b1.id)
        assertEquals("B", b2.id)

        assertEquals(1, b1.children.size)
        assertEquals("C", b1.children[0].id)

        assertEquals(1, b2.children.size)
        assertEquals("C", b2.children[0].id)
    }

    @Test
    fun `can parse interleaved output from multiple projects`() {
        val output = """
            PROJECT: :app | project ':app'
            PROJECT: :lib | project ':lib'
            CONFIGURATION: :app | implementation | App dependencies | true
            CONFIGURATION: :lib | implementation | Lib dependencies | true
            DEP: :app | * | :lib | project | :lib | | | | false
            DEP: :lib | * | org.jetbrains.kotlin:kotlin-stdlib:1.9.22 | org.jetbrains.kotlin | kotlin-stdlib | 1.9.22 | | | false
            DEP: :app | ** | org.jetbrains.kotlin:kotlin-stdlib:1.9.22 | org.jetbrains.kotlin | kotlin-stdlib | 1.9.22 | | | false
        """.trimIndent()

        val service = DefaultGradleDependencyService(MockGradleProvider())
        val report = service.parseStructuredOutput(output)

        assertEquals(2, report.projects.size)
        val app = report.projects.find { it.path == ":app" }!!
        val lib = report.projects.find { it.path == ":lib" }!!

        assertEquals(1, app.configurations[0].dependencies.size)
        assertEquals(1, lib.configurations[0].dependencies.size)

        val appDep = app.configurations[0].dependencies[0]
        assertEquals(":lib", appDep.id)

        val libDep = lib.configurations[0].dependencies[0]
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.22", libDep.id)

        // appDep's child (which is the stdlib) should have children from libDep
        assertEquals(1, appDep.children.size)
        val appStdLib = appDep.children[0]
        assertEquals("org.jetbrains.kotlin:kotlin-stdlib:1.9.22", appStdLib.id)
    }

    private class MockGradleProvider : GradleProvider {
        override suspend fun <T : Model> getBuildModel(
            projectRoot: GradleProjectRoot,
            kClass: KClass<T>,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
            progressHandler: ((Double, Double?, String?) -> Unit)?,
            requiresGradleProject: Boolean
        ): GradleResult<T> = throw UnsupportedOperationException()

        override fun runBuild(
            projectRoot: GradleProjectRoot,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
            progressHandler: ((Double, Double?, String?) -> Unit)?
        ): RunningBuild = throw UnsupportedOperationException()

        override fun runTests(
            projectRoot: GradleProjectRoot,
            testPatterns: Map<String, Set<String>>,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
            progressHandler: ((Double, Double?, String?) -> Unit)?
        ): RunningBuild = throw UnsupportedOperationException()

        override val buildManager: BuildManager
            get() = throw UnsupportedOperationException()

        override fun close() {}
    }
}
