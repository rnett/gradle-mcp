package dev.rnett.gradle.mcp.dependencies

import dev.rnett.gradle.mcp.dependencies.model.GradleDependency
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport

/**
 * Handles matching dependencies against regex filters on canonical coordinates and versions.
 */
internal class DependencyFilterMatcher(
    private val dependencyFilterRegex: Regex?,
    private val versionFilterRegex: Regex? = null
) {
    /**
     * Checks if a dependency matches the coordinate filter.
     */
    fun matchesDependency(dep: GradleDependency): Boolean {
        val filter = dependencyFilterRegex ?: return true
        return dep.canonicalCoordinateCandidates().any { filter.matches(it) }
    }

    /**
     * Checks if a dependency matches both the coordinate and version filters.
     */
    fun matches(dep: GradleDependency): Boolean {
        if (!matchesDependency(dep)) return false

        val filter = versionFilterRegex ?: return true
        val version = dep.latestVersion ?: dep.version ?: return false
        return version.matches(filter)
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

/**
 * Filter [report] keeping only dependencies that match [matcher].
 *
 * A dependency is kept if either:
 * - It itself matches the filter criteria, or
 * - At least one of its children is kept (preserving structural context)
 *
 * When [matcher] is null, the report is returned unchanged.
 */
internal fun filterDependencyTree(
    report: GradleDependencyReport,
    matcher: DependencyFilterMatcher?
): GradleDependencyReport {
    if (matcher == null) return report
    return report.copy(
        projects = report.projects.map { project ->
            project.copy(
                configurations = project.configurations.map { configuration ->
                    configuration.copy(dependencies = filterList(configuration.dependencies, matcher))
                }
            )
        }
    )
}

private fun filterList(dependencies: List<GradleDependency>, matcher: DependencyFilterMatcher): List<GradleDependency> =
    dependencies.mapNotNull { dependency ->
        val filteredChildren = filterList(dependency.children, matcher)
        if (matcher.matches(dependency) || filteredChildren.isNotEmpty()) {
            dependency.copy(children = filteredChildren)
        } else {
            null
        }
    }
