package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleDependency

/**
 * Handles matching of dependencies against regex filters on canonical dependency coordinates.
 */
internal class DependencyFilterMatcher(private val regex: Regex?) {
    /**
     * Checks if a dependency matches the filter.
     */
    fun matchesDependency(dep: GradleDependency): Boolean {
        val filter = regex ?: return true
        return dep.canonicalCoordinateCandidates().any { filter.matches(it) }
    }
}

internal fun normalizeDependencyFilter(filter: String?): String? = filter?.takeIf { it.isNotBlank() }

internal fun canonicalDependencyCoordinate(group: String?, name: String, version: String?, variant: String? = null): String = buildString {
    group?.takeIf { it.isNotBlank() }?.let {
        append(it)
        append(':')
    }
    append(name)
    version?.takeIf { it.isNotBlank() }?.let {
        append(':')
        append(it)
    }
    variant?.takeIf { it.isNotBlank() }?.let {
        append(':')
        append(it)
    }
}

internal fun dependencyCoordinateCandidates(group: String?, name: String, version: String?, variant: String? = null, unresolved: Boolean = false): List<String> {
    if (unresolved) {
        return listOf(canonicalDependencyCoordinate(group, name, null))
    }

    val coordinates = ArrayList<String>(2)
    if (!version.isNullOrBlank() && !variant.isNullOrBlank()) {
        coordinates.add(canonicalDependencyCoordinate(group, name, version, variant))
    }
    coordinates.add(canonicalDependencyCoordinate(group, name, version))
    return coordinates
}

internal fun GradleDependency.canonicalCoordinateCandidates(): List<String> =
    dependencyCoordinateCandidates(group, name, version, variant, unresolved = id.startsWith("UNRESOLVED:"))
