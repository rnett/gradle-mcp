package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.expandPath
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GradleInputsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `expandPath expands tilde dot and parent paths`() {
        val home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
        val current = Path.of(".").toAbsolutePath().normalize()
        val parent = Path.of("..").toAbsolutePath().normalize()

        assertEquals(home.toString(), "~".expandPath())
        assertEquals(home.resolve("test").toString(), "~/test".expandPath())
        assertEquals(current.toString(), ".".expandPath())
        assertEquals(parent.toString(), "..".expandPath())
    }

    @Test
    fun `resolve uses expanded normalized explicit path before environment`() {
        val explicit = tempDir.resolve("explicit").resolve("..").resolve("project")
        val environment = tempDir.resolve("environment")

        assertEquals(
            explicit.toAbsolutePath().normalize().absolutePathString(),
            GradleProjectRootInput(explicit.toString()).resolve(environment.toString()).projectRoot
        )
    }

    @Test
    fun `resolve uses expanded normalized environment fallback`() {
        val environment = tempDir.resolve("environment").resolve("..").resolve("project")

        assertEquals(
            environment.toAbsolutePath().normalize().absolutePathString(),
            GradleProjectRootInput.DEFAULT.resolve(environment.toString()).projectRoot
        )
    }

    @Test
    fun `resolve ignores blank explicit and environment roots`() {
        val environment = tempDir.resolve("environment")
        assertEquals(
            environment.absolutePathString(),
            GradleProjectRootInput("  ").resolve(environment.toString()).projectRoot
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            GradleProjectRootInput.DEFAULT.resolve("  ")
        }
        assertContains(failure.message.orEmpty(), "Provide projectRoot or set GRADLE_MCP_PROJECT_ROOT")
    }
}
