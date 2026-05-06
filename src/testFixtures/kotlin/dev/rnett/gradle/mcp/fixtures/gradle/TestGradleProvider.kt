package dev.rnett.gradle.mcp.fixtures.gradle

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.Model
import kotlin.reflect.KClass

/** JVM heap size used for all test Gradle daemons. */
private const val TEST_DAEMON_HEAP = "256m"

private val defaultTestGradleSystemProperties: Map<String, String> = linkedMapOf(
    "org.gradle.jvmargs" to "-Xmx$TEST_DAEMON_HEAP",
    "org.gradle.workers.max" to "2",
    "org.gradle.vfs.watch" to "false",
    "org.gradle.caching" to "true",
    "org.gradle.configuration-cache" to "true",
    "org.gradle.configuration-cache.parallel" to "true"
)

fun GradleProvider.withTestGradleDefaults(
    additionalSystemProps: Map<String, String> = emptyMap()
): GradleProvider = TestGradleProvider(this, additionalSystemProps)

fun GradleInvocationArguments.withTestGradleDefaults(
    additionalSystemProps: Map<String, String> = emptyMap()
): GradleInvocationArguments {
    val systemProps = defaultTestGradleSystemProperties
        .withOverriddenSystemProperties(additionalSystemProps)
        .withOverriddenSystemProperties(this.additionalSystemProps)

    val cacheArgs = listOf(
        if (systemProps["org.gradle.configuration-cache"] == "false") "--no-configuration-cache" else "--configuration-cache",
        if (systemProps["org.gradle.caching"] == "false") "--no-build-cache" else "--build-cache"
    )

    return copy(
        additionalSystemProps = systemProps,
        additionalArguments = cacheArgs + additionalArguments
    )
}

private fun Map<String, String>.withOverriddenSystemProperties(
    overrides: Map<String, String>
): Map<String, String> = this + overrides

private class TestGradleProvider(
    private val delegate: GradleProvider,
    private val additionalSystemProps: Map<String, String>
) : GradleProvider {
    override val buildManager: BuildManager
        get() = delegate.buildManager

    override suspend fun <T : Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter,
        requiresGradleProject: Boolean
    ): GradleResult<T> {
        return delegate.getBuildModel(
            projectRoot = projectRoot,
            kClass = kClass,
            args = args.withTestGradleDefaults(additionalSystemProps),
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progress = progress,
            requiresGradleProject = requiresGradleProject
        )
    }

    override fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter
    ): RunningBuild {
        return delegate.runBuild(
            projectRoot = projectRoot,
            args = args.withTestGradleDefaults(additionalSystemProps),
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progress = progress
        )
    }

    override fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter
    ): RunningBuild {
        return delegate.runTests(
            projectRoot = projectRoot,
            testPatterns = testPatterns,
            args = args.withTestGradleDefaults(additionalSystemProps),
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progress = progress
        )
    }

    override fun close() {
        delegate.close()
    }
}
