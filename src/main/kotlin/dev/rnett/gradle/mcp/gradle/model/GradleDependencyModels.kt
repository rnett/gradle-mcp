package dev.rnett.gradle.mcp.gradle.model

data class GradleDependencyReport(
    val projects: List<GradleProjectDependencies>
)

data class GradleProjectDependencies(
    val path: String,
    val sourceSets: List<GradleSourceSetDependencies>,
    val repositories: List<GradleRepository>,
    val configurations: List<GradleConfigurationDependencies>
)

data class GradleSourceSetDependencyReport(
    val name: String,
    val configurations: List<GradleConfigurationDependencies>,
    val repositories: List<GradleRepository>
)

data class GradleSourceSetDependencies(
    val name: String,
    val configurations: List<String>
)

data class GradleRepository(
    val name: String,
    val url: String?
)

data class GradleConfigurationDependencies(
    val name: String,
    val description: String?,
    val isResolvable: Boolean,
    val dependencies: List<GradleDependency>
)

data class GradleDependency(
    val id: String,
    val group: String?,
    val name: String,
    val version: String?,
    val reason: String? = null,
    val isAlreadyVisited: Boolean = false,
    val children: List<GradleDependency> = emptyList()
)
