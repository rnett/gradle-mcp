package dev.rnett.gradle.mcp.dependencies.model

import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradleDependencyModelsTest {

    @Test
    fun `test relativePrefix format`() {
        val dep = GradleDependency(
            id = "org.example:foo:1.0",
            group = "org.example",
            name = "foo",
            version = "1.0",
            sourcesFile = Path("foo-sources.jar")
        )

        // Should omit version as per design spec
        assertEquals("org.example/foo", dep.relativePrefix)
    }

    @Test
    fun `test relativePrefix with blank group`() {
        val dep = GradleDependency(
            id = "foo:1.0",
            group = "",
            name = "foo",
            version = "1.0",
            sourcesFile = Path("foo-sources.jar")
        )

        assertEquals("no-group/foo", dep.relativePrefix)
    }

    @Test
    fun `test relativePrefix without sources`() {
        val dep = GradleDependency(
            id = "org.example:foo:1.0",
            group = "org.example",
            name = "foo",
            version = "1.0",
            sourcesFile = null
        )

        assertNull(dep.relativePrefix)
    }
}
