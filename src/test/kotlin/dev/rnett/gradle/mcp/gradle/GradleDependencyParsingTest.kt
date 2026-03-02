package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.Model
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradleDependencyParsingTest {

    @Test
    fun `can parse structured output`() {
        val output = """
            PROJECT: : | project ':'
            REPOSITORY: : | MavenRepo | https://repo.maven.apache.org/maven2/
            SOURCESET: : | main | implementation,runtimeOnly,compileClasspath,runtimeClasspath
            CONFIGURATION: : | implementation | Implementation only dependencies for source set 'main'. | true
            DEP: : | * | project : | project | : | | | false
            DEP: : | ** | org.jetbrains.kotlin:kotlin-stdlib:1.9.22 | org.jetbrains.kotlin | kotlin-stdlib | 1.9.22 | | false
            DEP: : | *** | org.jetbrains:annotations:13.0 | org.jetbrains | annotations | 13.0 | | false
            CONFIGURATION: : | testImplementation | Implementation only dependencies for source set 'test'. | true
            DEP: : | * | project : | project | : | | | false
            DEP: : | ** | org.junit.jupiter:junit-jupiter:5.10.1 | org.junit.jupiter | junit-jupiter | 5.10.1 | | false
            DEP: : | *** | org.junit.jupiter:junit-jupiter-api:5.10.1 | org.junit.jupiter | junit-jupiter-api | 5.10.1 | | false
            DEP: : | *** | org.junit.jupiter:junit-jupiter-params:5.10.1 | org.junit.jupiter | junit-jupiter-params | 5.10.1 | | false
            DEP: : | *** | org.junit.jupiter:junit-jupiter-engine:5.10.1 | org.junit.jupiter | junit-jupiter-engine | 5.10.1 | | false
            DEP: : | **** | org.junit.platform:junit-platform-engine:1.10.1 | org.junit.platform | junit-platform-engine | 1.10.1 | | false
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
            DEP: : | * | project : | project | : | | | false
            DEP: : | ** | org.jetbrains.kotlin:kotlin-stdlib:1.9.22 | org.jetbrains.kotlin | kotlin-stdlib | 1.9.22 | | false
            DEP: : | *** | org.jetbrains:annotations:13.0 | org.jetbrains | annotations | 13.0 | | false
            DEP: : | ** | org.jetbrains:annotations:13.0 | org.jetbrains | annotations | 13.0 | | true
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
        assertTrue(secondAnnotations.isAlreadyVisited)
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
            requiresGradleProject: Boolean
        ): GradleResult<T> = throw UnsupportedOperationException()

        override fun runBuild(
            projectRoot: GradleProjectRoot,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?
        ): RunningBuild = throw UnsupportedOperationException()

        override fun runTests(
            projectRoot: GradleProjectRoot,
            testPatterns: Map<String, Set<String>>,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?
        ): RunningBuild = throw UnsupportedOperationException()

        override val buildManager: BuildManager
            get() = throw UnsupportedOperationException()

        override fun close() {}
    }
}
