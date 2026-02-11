package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine

class BackgroundBuildManager {
    private val builds = Caffeine.newBuilder()
        .maximumSize(1000)
        .evictionListener<BuildId, IRunningBuild<*>> { key, value, cause ->
            if (value?.status == BuildStatus.RUNNING) {
                value.stop()
            }
        }
        .build<BuildId, IRunningBuild<*>>()

    fun registerBuild(build: IRunningBuild<*>) {
        builds.put(build.id, build)
    }

    fun getBuild(id: BuildId): IRunningBuild<*>? = builds.getIfPresent(id)

    fun listBuilds(): List<IRunningBuild<*>> = builds.asMap().values.toList()

    internal fun removeBuild(id: BuildId) {
        builds.asMap().computeIfPresent(id) { _, build ->
            if (build.status == BuildStatus.RUNNING) build else null
        }
    }
}
