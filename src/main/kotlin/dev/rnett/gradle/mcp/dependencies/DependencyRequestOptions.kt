package dev.rnett.gradle.mcp.dependencies

/**
 * Options for dependency requests.
 *
 * @property configuration The name of the configuration to get dependencies for (e.g. "implementation"). If null, all configurations are included.
 * @property sourceSet The name of the source set to get dependencies for (e.g. "main"). If null, all source sets are included.
 * @property dependency The coordinates of the dependency to filter by (e.g. "group:name").
 * @property checkUpdates Whether to check for dependency updates.
 * @property versionFilter Regex filter for considered update versions.
 * @property stableOnly Whether to only include stable versions when checking for updates.
 * @property onlyDirect Whether to only include direct dependencies in the report.
 * @property downloadSources Whether to download and include source artifacts.
 * @property excludeBuildscript Whether to exclude buildscript dependencies from the report. Defaults to true.
 * @property fresh Whether to force a fresh dependency resolution.
 * @property includeInternal Whether to include internal configurations (e.g. KMP metadata) in the report. Defaults to false.
 */
data class DependencyRequestOptions(
    val configuration: String? = null,
    val sourceSet: String? = null,
    val dependency: String? = null,
    val checkUpdates: Boolean = false,
    val versionFilter: String? = null,
    val stableOnly: Boolean = false,
    val onlyDirect: Boolean = false,
    val downloadSources: Boolean = false,
    val excludeBuildscript: Boolean = true,
    val fresh: Boolean = false,
    val includeInternal: Boolean = false
)
