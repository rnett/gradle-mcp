package dev.rnett.gradle.mcp.dependencies.model

import java.nio.file.Path

data class GradleDependencyReport(
    val projects: List<GradleProjectDependencies>
)

data class GradleProjectDependencies(
    val path: String,
    val sourceSets: List<GradleSourceSetDependencies>,
    val repositories: List<GradleRepository>,
    val configurations: List<GradleConfigurationDependencies>
) {
    fun allDependencies(): Sequence<GradleDependency> {
        val resolved = mutableMapOf<Triple<String, String?, List<String>>, GradleDependency>()
        val unresolved = mutableMapOf<String, GradleDependency>()

        configurations.forEach { config ->
            config.allDependencies().forEach { dep ->
                val isUnresolved = dep.id.startsWith("UNRESOLVED:")
                val id = dep.id.removePrefix("UNRESOLVED:")
                val key = Triple(id, dep.variant, dep.capabilities)

                if (isUnresolved) {
                    if (resolved.keys.none { it.first == id }) {
                        val existing = unresolved[id]
                        if (existing == null || (existing.children.isEmpty() && dep.children.isNotEmpty())) {
                            unresolved[id] = dep
                        }
                    }
                } else {
                    val existing = resolved[key]
                    if (existing == null || (existing.children.isEmpty() && dep.children.isNotEmpty())) {
                        resolved[key] = dep
                    }
                    unresolved.remove(id)
                }
            }
        }
        return (resolved.values.asSequence() + unresolved.values.asSequence())
    }

    fun allDependencies(configurationName: String): List<GradleDependency> {
        val resolved = mutableMapOf<Triple<String, String?, List<String>>, GradleDependency>()
        val unresolved = mutableMapOf<String, GradleDependency>()

        fun collect(name: String) {
            val current = configurations.find { it.name == name } ?: return
            current.dependencies.forEach { dep ->
                val isUnresolved = dep.id.startsWith("UNRESOLVED:")
                val id = dep.id.removePrefix("UNRESOLVED:")
                val key = Triple(id, dep.variant, dep.capabilities)

                if (isUnresolved) {
                    if (resolved.keys.none { it.first == id }) {
                        val existing = unresolved[id]
                        if (existing == null || (existing.children.isEmpty() && dep.children.isNotEmpty())) {
                            unresolved[id] = dep
                        }
                    }
                } else {
                    val existing = resolved[key]
                    if (existing == null || (existing.children.isEmpty() && dep.children.isNotEmpty())) {
                        resolved[key] = dep
                    }
                    unresolved.remove(id)
                }
            }

            current.extendsFrom.forEach { collect(it) }
        }

        collect(configurationName)
        return (resolved.values + unresolved.values).toList()
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
) {
    fun allDependencies(): Sequence<GradleDependency> {
        return dependencies.asSequence().flatMap { it.allDependencies() }
    }
}

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
    val sourcesFile: Path? = null,
    val updatesChecked: Boolean = false,
    val children: List<GradleDependency> = emptyList()
) {
    val hasSources: Boolean get() = sourcesFile != null && group != null && version != null

    /**
     * The relative path prefix for this dependency's sources in the session view.
     * Format: `deps/{name}/{version}` — clearly non-package-like, making paths unambiguous.
     * The junction at this prefix points to the CAS `v1/` normalized directory.
     */
    val relativePrefix: String? by lazy {
        val v = version ?: return@lazy null
        if (sourcesFile == null) return@lazy null
        "deps/$name/$v"
    }

    fun allDependencies(): Sequence<GradleDependency> {
        return sequence {
            yield(this@GradleDependency)
            children.forEach {
                yieldAll(it.allDependencies())
            }
        }
    }
}
