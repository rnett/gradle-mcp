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

    fun getBuild(id: BuildId): IRunningBuild<*>? {
        val build = builds.getIfPresent(id)
        if (build != null && build.status != BuildStatus.RUNNING) {
            builds.invalidate(id)
            return null
        }
        return build
    }

    fun listBuilds(): List<IRunningBuild<*>> {
        val allBuilds = builds.asMap().values.toList()
        allBuilds.forEach {
            if (it.status != BuildStatus.RUNNING) {
                builds.invalidate(it.id)
            }
        }
        return allBuilds.filter { it.status == BuildStatus.RUNNING }
    }

    internal fun removeBuild(id: BuildId) {
        builds.invalidate(id)
    }
}
