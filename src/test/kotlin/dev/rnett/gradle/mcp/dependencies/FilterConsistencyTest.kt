package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterConsistencyTest {

    private fun dep(group: String, name: String, version: String, variant: String? = null) = GradleDependency(
        id = "$group:$name:$version",
        group = group,
        name = name,
        version = version,
        variant = variant,
        sourcesFile = null
    )

    private interface MockModuleId {
        val group: String
        val module: String
        val version: String
    }

    private fun moduleId(group: String, module: String, version: String) = object : MockModuleId {
        override val group = group
        override val module = module
        override val version = version
    }

    /**
     * Re-implementation of the logic in `dependencies-report.init.gradle.kts`.
     * If this changes, it MUST be updated here.
     */
    private fun matchesFilterKts(id: MockModuleId, filter: String?): Boolean {
        if (filter == null) return true
        val filterParts = filter.split(":", limit = 4)
        val group = id.group
        val module = id.module
        val version = id.version

        return when (filterParts.size) {
            1 -> group == filterParts[0]
            2 -> group == filterParts[0] && module == filterParts[1]
            else -> group == filterParts[0] && module == filterParts[1] && version == filterParts[2]
        }
    }

    private fun assertConsistent(group: String, name: String, version: String, filter: String, variant: String? = null) {
        val d = dep(group, name, version, variant)
        val id = moduleId(group, name, version)

        val serviceResult = DependencyFilterMatcher(filter).matches(d)
        val ktsResult = matchesFilterKts(id, filter)

        assertEquals(ktsResult, serviceResult, "Inconsistency for filter '$filter' with $group:$name:$version (variant=$variant)")
    }

    @Test
    fun `matchesFilter is consistent for 1-part filters`() {
        assertConsistent("org.example", "artifact", "1.0.0", "org.example")
        assertConsistent("org.example", "artifact", "1.0.0", "org.other")
    }

    @Test
    fun `matchesFilter is consistent for 2-part filters`() {
        assertConsistent("org.example", "artifact", "1.0.0", "org.example:artifact")
        assertConsistent("org.example", "artifact", "1.0.0", "org.example:other")
    }

    @Test
    fun `matchesFilter is consistent for 3-part filters`() {
        assertConsistent("org.example", "artifact", "1.0.0", "org.example:artifact:1.0.0")
        assertConsistent("org.example", "artifact", "1.0.0", "org.example:artifact:2.0.0")
    }

    @Test
    fun `matchesFilter behaves correctly with variants (service only)`() {
        val d = dep("org.example", "artifact", "1.0.0", "jvm")
        val id = moduleId("org.example", "artifact", "1.0.0")

        val filter = "org.example:artifact:1.0.0:jvm"
        val serviceResult = DependencyFilterMatcher(filter).matches(d)
        val ktsResult = matchesFilterKts(id, filter)

        // KTS should still match on G:A:V for a 4-part filter
        assertTrue(ktsResult, "KTS should match G:A:V even for 4-part filter")
        assertTrue(serviceResult, "Service should match G:A:V:Variant")
    }
}
