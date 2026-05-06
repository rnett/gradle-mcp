package dev.rnett.gradle.mcp.dependencies.model

import java.nio.file.Path

data class GradleDependencyReport(
    val projects: List<GradleProjectDependencies>,
    val notes: List<String> = emptyList()
)

data class GradleProjectDependencies(
    val path: String,
    val sourceSets: List<GradleSourceSetDependencies>,
    val repositories: List<GradleRepository>,
    val configurations: List<GradleConfigurationDependencies>,
    val jdkHome: String? = null,
    val jdkVersion: String? = null
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

        val result = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.addAll(config.extendsFrom)

        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            if (result.add(current)) {
                configurations.find { it.name == current }?.extendsFrom?.let {
                    stack.addAll(it)
                }
            }
        }
        return result.size
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
    val repositories: List<GradleRepository>,
    val isJvm: Boolean = false
)

data class GradleSourceSetDependencies(
    val name: String,
    val configurations: List<String>,
    val isJvm: Boolean = false
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
    val dependencies: List<GradleDependency>,
    val isInternal: Boolean = false
) {
    fun allDependencies(): Sequence<GradleDependency> = sequence {
        val stack = dependencies.reversed().toMutableList()
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            yield(current)
            for (i in current.children.indices.reversed()) {
                stack.add(current.children[i])
            }
        }
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
    val commonComponentId: String? = null,
    val sourcesFile: Path? = null,
    val updatesChecked: Boolean = false,
    val children: List<GradleDependency> = emptyList()
) {
    companion object {
        const val JDK_SOURCES_GROUP = "jdk"
        const val JDK_SOURCES_NAME = "sources"
        const val JDK_SOURCES_PREFIX = "$JDK_SOURCES_GROUP/$JDK_SOURCES_NAME"

        fun jdkSources(hash: String, sourcesFile: Path): GradleDependency = GradleDependency(
            id = "JDK:$hash",
            group = JDK_SOURCES_GROUP,
            name = JDK_SOURCES_NAME,
            version = null,
            sourcesFile = sourcesFile
        )
    }

    val hasSources: Boolean get() = sourcesFile != null

    /**
     * The relative path prefix for this dependency's sources in the session view.
     * Format: `{group}/{name}` — scopes libraries by their group and name.
     * The junction at this prefix points to the current CAS normalized directory.
     */
    val relativePrefix: String? by lazy {
        if (sourcesFile == null) return@lazy null
        val g = group?.takeIf { it.isNotBlank() } ?: "no-group"
        "$g/$name"
    }

    fun allDependencies(): Sequence<GradleDependency> = sequence {
        val stack = mutableListOf(this@GradleDependency)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            yield(current)
            for (i in current.children.indices.reversed()) {
                stack.add(current.children[i])
            }
        }
    }
}
