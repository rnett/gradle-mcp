package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.gradle.GradleProvider
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleDependencyServiceTest {

    private val mockGradleProvider: GradleProvider = mockk(relaxed = true)
    private val service = DefaultGradleDependencyService(mockGradleProvider)

    @Test
    fun `parse JDK line`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] JDK | : | /path/to/jdk | 21
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertEquals("/path/to/jdk", project.jdkHome)
        assertEquals("21", project.jdkVersion)
    }

    @Test
    fun `no JDK line results in null JDK home and version`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertNull(project.jdkHome)
        assertNull(project.jdkVersion)
    }

    @Test
    fun `JDK line with empty version`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] JDK | : | /path/to/jdk | 
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertEquals("/path/to/jdk", project.jdkHome)
        assertNull(project.jdkVersion)
    }

    @Test
    fun `JDK line for subproject`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | :app | app
            [gradle-mcp] [DEPENDENCIES] JDK | :app | /path/to/jdk | 17
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertEquals(":app", project.path)
        assertEquals("/path/to/jdk", project.jdkHome)
        assertEquals("17", project.jdkVersion)
    }

    @Test
    fun `JDK line with escaped pipe in path`() {
        // The init script escapes pipes with \| and the parser now unescapes them.
        // The path /path/to/jdk\|21 becomes /path/to/jdk|21 after unescaping.
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] JDK | : | /path/to/jdk\|21 | 21
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertEquals("/path/to/jdk|21", project.jdkHome)
        assertEquals("21", project.jdkVersion)
    }

    @Test
    fun `multiple projects with different JDK lines`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] JDK | : | /path/to/jdk21 | 21
            [gradle-mcp] [DEPENDENCIES] PROJECT | :app | app
            [gradle-mcp] [DEPENDENCIES] JDK | :app | /path/to/jdk17 | 17
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        assertEquals(2, report.projects.size)

        val rootProject = report.projects.find { it.path == ":" }!!
        assertEquals("/path/to/jdk21", rootProject.jdkHome)
        assertEquals("21", rootProject.jdkVersion)

        val appProject = report.projects.find { it.path == ":app" }!!
        assertEquals("/path/to/jdk17", appProject.jdkHome)
        assertEquals("17", appProject.jdkVersion)
    }

    @Test
    fun `JDK line with version containing special chars`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] JDK | : | /path/to/jdk | 21.0.5+7-LTS
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertEquals("/path/to/jdk", project.jdkHome)
        assertEquals("21.0.5+7-LTS", project.jdkVersion)
    }

    @Test
    fun `JDK line with Windows path`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] JDK | : | C:\Program Files\Java\jdk-21 | 21
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertEquals("C:\\Program Files\\Java\\jdk-21", project.jdkHome)
        assertEquals("21", project.jdkVersion)
    }

    @Test
    fun `SOURCESET line parses JVM flag`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] SOURCESET | : | main | implementation,runtimeOnly | true
            [gradle-mcp] [DEPENDENCIES] SOURCESET | : | commonMain | commonMainImplementation | false
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertTrue(project.sourceSets.single { it.name == "main" }.isJvm)
        assertFalse(project.sourceSets.single { it.name == "commonMain" }.isJvm)
    }

    @Test
    fun `SOURCESET parser normalizes stale Kotlin prefix and merges duplicates`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] SOURCESET | : | main | implementation | true
            [gradle-mcp] [DEPENDENCIES] SOURCESET | : | kotlin:main | kotlinCompilerClasspath | false
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val sourceSet = report.projects.first().sourceSets.single { it.name == "main" }
        assertTrue(sourceSet.isJvm)
        assertEquals(listOf("implementation", "kotlinCompilerClasspath"), sourceSet.configurations)
    }

    @Test
    fun `SOURCESET line without JVM flag defaults to non-JVM except buildscript`() {
        val output = """
            [gradle-mcp] [DEPENDENCIES] PROJECT | : | root
            [gradle-mcp] [DEPENDENCIES] SOURCESET | : | main | implementation
            [gradle-mcp] [DEPENDENCIES] SOURCESET | : | __mcp_buildscript__ | buildscript:classpath
        """.trimIndent()

        val report = service.parseStructuredOutput(output)
        val project = report.projects.first()
        assertFalse(project.sourceSets.single { it.name == "main" }.isJvm)
        assertTrue(project.sourceSets.single { it.name == "buildscript" }.isJvm)
    }
}
