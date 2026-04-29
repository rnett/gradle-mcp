package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleDependency

/**
 * Handles matching of dependencies against filter strings (group:name:version:variant).
 */
class DependencyFilterMatcher(val dependencyFilter: String?) {

    private val parts: List<String> by lazy {
        dependencyFilter?.split(":", limit = 4) ?: emptyList()
    }

    /**
     * Returns true if the filter is provided and does not include a version (fewer than 3 parts).
     */
    val isVersionLess: Boolean
        get() = dependencyFilter != null && parts.size < 3

    /**
     * Checks if a dependency matches the filter.
     * Note: This logic is partially duplicated in `dependencies-report.init.gradle.kts`.
     * The init script only filters up to 3 parts (G:A:V) because variant info isn't easily available there.
     * This method provides the precise final filtering, safely handling any over-fetching by the init script.
     */
    fun matchesDependency(dep: GradleDependency): Boolean {
        if (dependencyFilter == null) return true

        val group = dep.group
        val name = dep.name
        val version = dep.version
        val variant = dep.variant

        return when (parts.size) {
            1 -> group == parts[0]
            2 -> group == parts[0] && name.startsWith(parts[1])
            3 -> group == parts[0] && (name == parts[1] || name.startsWith("${parts[1]}-")) && version == parts[2]
            4 -> group == parts[0] && name == parts[1] && version == parts[2] && variant == parts[3]
            else -> false
        }
    }
}
