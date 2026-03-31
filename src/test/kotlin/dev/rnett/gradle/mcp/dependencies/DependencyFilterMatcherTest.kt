package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DependencyFilterMatcherTest {

    private fun dep(group: String, name: String, version: String, variant: String? = null) = GradleDependency(
        id = "$group:$name:$version",
        group = group,
        name = name,
        version = version,
        variant = variant,
        sourcesFile = null
    )

    private fun matchesFilter(dep: GradleDependency, filter: String): Boolean {
        return DependencyFilterMatcher(filter).matchesDependency(dep)
    }

    @Test
    fun `matchesFilter handles group only`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "org.example"))
        assertFalse(matchesFilter(d, "org.other"))
    }

    @Test
    fun `matchesFilter handles group and name`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "org.example:artifact"))
        assertFalse(matchesFilter(d, "org.example:other"))
        assertFalse(matchesFilter(d, "other:artifact"))
    }

    @Test
    fun `matchesFilter handles group name and version`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "org.example:artifact:1.0.0"))
        assertFalse(matchesFilter(d, "org.example:artifact:2.0.0"))
    }

    @Test
    fun `matchesFilter handles variant`() {
        val d = dep("org.example", "artifact", "1.0.0", "jvm")
        assertTrue(matchesFilter(d, "org.example:artifact:1.0.0:jvm"))
        assertFalse(matchesFilter(d, "org.example:artifact:1.0.0:android"))
    }

    @Test
    fun `matchesFilter handles null variant`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "org.example:artifact:1.0.0"))
        assertFalse(matchesFilter(d, "org.example:artifact:1.0.0:jvm"))
    }

    @Test
    fun `matchesFilter rejects invalid filters`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertFalse(matchesFilter(d, "org.example:artifact:1.0.0:jvm:extra"))
    }
}
