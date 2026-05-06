package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyFilterMatcherTest {

    private fun dep(group: String, name: String, version: String? = "1.0.0", variant: String? = null, unresolved: Boolean = false) = GradleDependency(
        id = if (unresolved) "UNRESOLVED:$group:$name" else "$group:$name:$version",
        group = group,
        name = name,
        version = version,
        variant = variant,
        sourcesFile = null
    )

    private fun matchesFilter(dep: GradleDependency, filter: String): Boolean {
        return DependencyFilterMatcher(Regex(filter)).matchesDependency(dep)
    }

    @Test
    fun `null regex matches every dependency`() {
        val d = dep("org.example", "artifact", "1.0.0")

        assertTrue(DependencyFilterMatcher(null).matchesDependency(d))
    }

    @Test
    fun `matchesFilter handles group only`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "^org\\.example(:.*)?$"))
        assertFalse(matchesFilter(d, "^org\\.other(:.*)?$"))
    }

    @Test
    fun `matchesFilter handles group and name`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "^org\\.example:artifact(:.*)?$"))
        assertFalse(matchesFilter(d, "^org\\.example:other(:.*)?$"))
        assertFalse(matchesFilter(d, "^other:artifact(:.*)?$"))

        // Regex prefix matching for KMP support
        val kmp = dep("ai.koog", "prompt-structure-jvm", "0.0.1")
        assertTrue(matchesFilter(kmp, "^ai\\.koog:prompt-structure.*$"))
        assertTrue(matchesFilter(kmp, "^ai\\.koog:prompt.*$"))
    }

    @Test
    fun `matchesFilter handles group name and version`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0$"))
        assertFalse(matchesFilter(d, "^org\\.example:artifact:2\\.0\\.0$"))
    }

    @Test
    fun `matchesFilter handles variant`() {
        val d = dep("org.example", "artifact", "1.0.0", "jvm")
        assertTrue(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0$"))
        assertTrue(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0:jvm$"))
        assertFalse(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0:android$"))
    }

    @Test
    fun `matchesFilter handles null variant`() {
        val d = dep("org.example", "artifact", "1.0.0")
        assertTrue(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0$"))
        assertFalse(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0:jvm$"))
    }

    @Test
    fun `matchesFilter handles unresolved group and name fallback`() {
        val d = dep("org.example", "artifact", version = null, unresolved = true)
        assertTrue(matchesFilter(d, "^org\\.example:artifact$"))
        assertFalse(matchesFilter(d, "^org\\.example:artifact:1\\.0\\.0$"))
    }

}
