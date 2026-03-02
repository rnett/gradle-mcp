package dev.rnett.gradle.mcp.gradle.dependencies.model

data class GradleDependencyReport(
    val projects: List<GradleProjectDependencies>
)

data class GradleProjectDependencies(
    val path: String,
    val sourceSets: List<GradleSourceSetDependencies>,
    val repositories: List<GradleRepository>,
    val configurations: List<GradleConfigurationDependencies>
) {
    fun allDependencies(configurationName: String): List<GradleDependency> {
        val allDeps = mutableMapOf<Any, GradleDependency>()

        fun collect(name: String) {
            val current = configurations.find { it.name == name } ?: return

            // Collect dependencies from this configuration.
            // If already present, only update if the new one has children (is resolved) 
            // while the old one doesn't.
            current.dependencies.forEach { dep ->
                val key = listOf(dep.id, dep.variant, dep.capabilities)
                val existing = allDeps[key]
                if (existing == null || (existing.children.isEmpty() && dep.children.isNotEmpty())) {
                    allDeps[key] = dep
                }
            }

            current.extendsFrom.forEach { collect(it) }
        }

        collect(configurationName)
        return allDeps.values.toList()
    }

    fun configurationDepth(name: String): Int {
        val config = configurations.find { it.name == name } ?: return 0
        if (config.extendsFrom.isEmpty()) return 0

        val visited = mutableSetOf<String>()
        fun getTransitiveParents(current: String): Set<String> {
            if (current in visited) return emptySet()
            visited.add(current)
            val c = configurations.find { it.name == current } ?: return emptySet()
            return c.extendsFrom.toSet() + c.extendsFrom.flatMap { getTransitiveParents(it) }
        }

        return config.extendsFrom.flatMap { getTransitiveParents(it) }.toSet().size + config.extendsFrom.size
    }

    fun transitiveExtendsFrom(name: String): Set<String> {
        val config = configurations.find { it.name == name } ?: return emptySet()
        val result = mutableSetOf<String>()
        fun collect(current: String) {
            val c = configurations.find { it.name == current } ?: return
            c.extendsFrom.forEach { parent ->
                if (result.add(parent)) {
                    collect(parent)
                }
            }
        }
        collect(name)
        return result
    }
}

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
    val extendsFrom: List<String> = emptyList(),
    val dependencies: List<GradleDependency>
)

data class GradleDependency(
    val id: String,
    val group: String?,
    val name: String,
    val version: String?,
    val variant: String? = null,
    val capabilities: List<String> = emptyList(),
    val latestVersion: String? = null,
    val isDirect: Boolean = false,
    val fromConfiguration: String? = null,
    val reason: String? = null,
    val children: List<GradleDependency> = emptyList()
)
