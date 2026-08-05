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

/**
 * Test-only daemon idle timeout (ms). Nested test builds run through the Tooling API, which cannot
 * stop daemons, so this makes stragglers self-expire instead of lingering for Gradle's default
 * multi-hour idle timeout.
 */
internal const val TEST_DAEMON_IDLE_TIMEOUT_MS = 120_000

/**
 * Canonical JVM args for nested test daemons: the test heap.
 *
 * Applied through the Tooling API JVM-arguments channel ([GradleInvocationArguments.additionalJvmArgs] ->
 * `Launcher.addJvmArguments`, BuildExecutionService.kt:165) — verified to reach the daemon JVM by the
 * task 3.1 probe (daemon context shows `-Xmx256m` replacing the user-home `-Xmx3g`). `-D` system
 * properties passed through this channel are extracted by `JvmOptions` into build system properties
 * and do NOT affect the daemon JVM, so the idle timeout is delivered as a launcher argument instead
 * (see [TEST_DAEMON_IDLE_TIMEOUT_ARG]).
 */
internal val TEST_DAEMON_JVM_ARGS: List<String> = listOf("-Xmx$TEST_DAEMON_HEAP")

/**
 * Launcher argument that sets the test-only daemon idle timeout.
 *
 * Passed as a raw `-D` command-line argument (not a Tooling API system property):
 * `ProviderConnection.initParams` feeds initial `-D` arguments into `DaemonBuildOptions` AFTER the
 * user-home gradle.properties conversion, so this overrides `~/.gradle/gradle.properties` and reaches
 * `DaemonParameters.idleTimeout` (source-verified in Gradle 9.6.1; see probes/FINDINGS-task3.1.md).
 */
internal val TEST_DAEMON_IDLE_TIMEOUT_ARG = "-Dorg.gradle.daemon.idletimeout=$TEST_DAEMON_IDLE_TIMEOUT_MS"

private val defaultTestGradleSystemProperties: Map<String, String> = linkedMapOf(
    // Daemon JVM args (heap) are applied via the Tooling API JVM-arguments channel (TEST_DAEMON_JVM_ARGS);
    // an `org.gradle.jvmargs` system property does not influence daemon startup.
    "org.gradle.workers.max" to "2",
    "org.gradle.vfs.watch" to "false",
    "org.gradle.caching" to "true",
    "org.gradle.configuration-cache" to "true",
    "org.gradle.configuration-cache.parallel" to "true"
)

/**
 * Wraps a [GradleProvider] so every nested build routes through the canonical test defaults
 * ([withTestGradleDefaults]).
 *
 * @param pinJavaHome when true (default), the launcher `javaHome` is filled in with the test-worker
 *   JDK (`System.getProperty("java.home")`) whenever the caller has not set one explicitly, so the
 *   inherited `JAVA_HOME` environment fallback cannot spawn a separate daemon pool. Set to false to
 *   opt out (dedicated fallback tests only); an explicit `javaHome` is never overwritten either way.
 */
fun GradleProvider.withTestGradleDefaults(
    additionalSystemProps: Map<String, String> = emptyMap(),
    pinJavaHome: Boolean = true
): GradleProvider = TestGradleProvider(this, additionalSystemProps, pinJavaHome)

/**
 * Applies the canonical test defaults to [GradleInvocationArguments].
 *
 * @param pinJavaHome when true (default), fills in the launcher `javaHome` with the test-worker JDK
 *   (`System.getProperty("java.home")`) only when the caller has not set one explicitly; an explicit
 *   `javaHome` always takes precedence. Set to false to keep the pre-change fallback behavior
 *   (environment `JAVA_HOME` / Tooling API default) for dedicated fallback tests.
 */
fun GradleInvocationArguments.withTestGradleDefaults(
    additionalSystemProps: Map<String, String> = emptyMap(),
    pinJavaHome: Boolean = true
): GradleInvocationArguments {
    val systemProps = defaultTestGradleSystemProperties
        .withOverriddenSystemProperties(additionalSystemProps)
        .withOverriddenSystemProperties(this.additionalSystemProps)

    val cacheArgs = listOf(
        if (systemProps["org.gradle.configuration-cache"] == "false") "--no-configuration-cache" else "--configuration-cache",
        if (systemProps["org.gradle.caching"] == "false") "--no-build-cache" else "--build-cache"
    )

    return copy(
        javaHome = if (pinJavaHome) javaHome ?: System.getProperty("java.home") else javaHome,
        additionalJvmArgs = TEST_DAEMON_JVM_ARGS + additionalJvmArgs,
        additionalSystemProps = systemProps,
        additionalArguments = cacheArgs + listOf(TEST_DAEMON_IDLE_TIMEOUT_ARG) + additionalArguments
    )
}

private fun Map<String, String>.withOverriddenSystemProperties(
    overrides: Map<String, String>
): Map<String, String> = this + overrides

private class TestGradleProvider(
    private val delegate: GradleProvider,
    private val additionalSystemProps: Map<String, String>,
    private val pinJavaHome: Boolean
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
            args = args.withTestGradleDefaults(additionalSystemProps, pinJavaHome),
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
            args = args.withTestGradleDefaults(additionalSystemProps, pinJavaHome),
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
            args = args.withTestGradleDefaults(additionalSystemProps, pinJavaHome),
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
