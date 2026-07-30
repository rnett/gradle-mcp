package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.expandPath
import io.modelcontextprotocol.kotlin.sdk.types.Root
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
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
    fun `resolveRoot accepts explicit paths when roots are unavailable`() {
        val input = GradleProjectRootInput(tempDir.toString())

        assertEquals(tempDir.absolutePathString(), input.resolveRoot(null, envRoot = null).projectRoot)
    }

    @Test
    fun `resolveRoot uses a single implicit root before environment fallback`() {
        val root = Root(tempDir.toUri().toString(), "workspace")

        assertEquals(tempDir.absolutePathString(), GradleProjectRootInput.DEFAULT.resolveRoot(setOf(root), tempDir.resolve("env").toString()).projectRoot)
    }

    @Test
    fun `resolveRoot uses environment fallback without configured roots`() {
        val envRoot = tempDir.resolve("from-env")

        assertEquals(envRoot.absolutePathString(), GradleProjectRootInput.DEFAULT.resolveRoot(emptySet(), envRoot.toString()).projectRoot)
    }

    @Test
    fun `resolveRoot rejects missing and ambiguous implicit roots`() {
        val noRoots = assertFailsWith<IllegalArgumentException> {
            GradleProjectRootInput.DEFAULT.resolveRoot(emptySet(), envRoot = null)
        }
        val multipleRoots = assertFailsWith<IllegalArgumentException> {
            GradleProjectRootInput.DEFAULT.resolveRoot(
                setOf(
                    Root(tempDir.resolve("one").toUri().toString(), "one"),
                    Root(tempDir.resolve("two").toUri().toString(), "two")
                ),
                envRoot = null
            )
        }

        assertContains(noRoots.message.orEmpty(), "No MCP roots configured")
        assertContains(multipleRoots.message.orEmpty(), "Multiple MCP roots configured")
    }

    @Test
    fun `resolveRoot resolves explicit names and contained paths`() {
        val workspace = tempDir.resolve("workspace").createDirectories()
        val project = workspace.resolve("project").createDirectories()
        val root = Root(workspace.toUri().toString(), "workspace")

        assertEquals(workspace.absolutePathString(), GradleProjectRootInput("workspace").resolveRoot(setOf(root), null).projectRoot)
        assertEquals(project.absolutePathString(), GradleProjectRootInput(project.toString()).resolveRoot(setOf(root), null).projectRoot)
    }

    @Test
    fun `resolveRoot rejects explicit paths outside configured roots`() {
        val workspace = tempDir.resolve("workspace").createDirectories()
        val outside = tempDir.resolve("outside").createDirectories()

        val failure = assertFailsWith<IllegalArgumentException> {
            GradleProjectRootInput(outside.toString()).resolveRoot(
                setOf(Root(workspace.toUri().toString(), "workspace")),
                envRoot = null
            )
        }

        assertContains(failure.message.orEmpty(), "is not in any of the configured MCP roots")
    }
}
