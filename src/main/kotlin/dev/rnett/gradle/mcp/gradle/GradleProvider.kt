@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.build.BuildExecutionService
import dev.rnett.gradle.mcp.gradle.build.DefaultBuildExecutionService
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.DefaultEnvProvider
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface GradleProvider : AutoCloseable {

    suspend fun <T : Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,

        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        progress: ProgressReporter = ProgressReporter.NONE,
        requiresGradleProject: Boolean = true
    ): GradleResult<T>

    fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        progress: ProgressReporter = ProgressReporter.NONE
    ): RunningBuild

    fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,

        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        progress: ProgressReporter = ProgressReporter.NONE
    ): RunningBuild

    val buildManager: BuildManager
}

class InterceptedSpecialCommandException(message: String) : IllegalStateException(message)

class DefaultGradleProvider(
    val config: GradleConfiguration,
    private val connectionService: GradleConnectionService,
    private val executionService: BuildExecutionService,
    override val buildManager: BuildManager
) : GradleProvider {

    constructor(
        config: GradleConfiguration,
        buildManager: BuildManager,
        envProvider: EnvProvider = DefaultEnvProvider,
        initScriptProvider: DefaultInitScriptProvider = DefaultInitScriptProvider()
    ) : this(
        config,
        DefaultGradleConnectionService(),
        DefaultBuildExecutionService(envProvider, initScriptProvider),
        buildManager
    )

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultGradleProvider::class.java)
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            this.close()
        })
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            buildManager.listRunningBuilds().forEach { it.stop() }
            scope.cancel()
        }
    }

    fun getConnection(projectRoot: Path): ProjectConnection = connectionService.connect(projectRoot)

    private fun <I : ConfigurableLauncher<*>, R> startBuild(
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter,
        launcherProvider: suspend (ProjectConnection) -> I,
        invoker: (I) -> R,
        requiresGradleProject: Boolean = true,
        projectRootInput: GradleProjectRoot
    ): Pair<RunningBuild, Deferred<Result<R>>> {
        val projectRoot = GradlePathUtils.getRootProjectPath(projectRootInput, requiresGradleProject)
        val buildId = buildManager.newId()
        val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
        val runningBuild = RunningBuild(
            id = buildId,
            args = args,
            startTime = Clock.System.now(),
            projectRoot = projectRoot,
            cancellationTokenSource = cancellationTokenSource
        ).also { buildManager.registerBuild(it) }

        val deferred = scope.async {
            val outcome: Result<R> = try {
                if (args.isHelp) {
                    val model = getBuildModel(
                        projectRootInput,
                        org.gradle.tooling.model.build.Help::class,
                        args.copy(
                            additionalArguments = emptyList(),
                            publishScan = false,
                            requestedInitScripts = emptyList()
                        ),
                        additionalProgressListeners,
                        stdoutLineHandler,
                        stderrLineHandler,
                        progress,
                        requiresGradleProject
                    )
                    val text = model.value.getOrThrow().renderedText
                    runningBuild.addLogLine(text)
                    stdoutLineHandler?.invoke(text)
                    Result.failure(InterceptedSpecialCommandException("Help command intercepted, result is in console output."))
                } else if (args.isVersion) {
                    val model = getBuildModel(
                        projectRootInput,
                        BuildEnvironment::class,
                        args.copy(
                            additionalArguments = emptyList(),
                            publishScan = false,
                            requestedInitScripts = emptyList()
                        ),
                        additionalProgressListeners,
                        stdoutLineHandler,
                        stderrLineHandler,
                        progress,
                        requiresGradleProject
                    )
                    val text = "Gradle ${model.value.getOrThrow().gradle.gradleVersion}"
                    runningBuild.addLogLine(text)
                    stdoutLineHandler?.invoke(text)
                    Result.failure(InterceptedSpecialCommandException("Version command intercepted, result is in console output."))
                } else {
                    connectionService.connect(projectRoot).use { connection ->
                        try {
                            val launcher = launcherProvider(connection)
                            Result.success(
                                executionService.invokeBuild(
                                    launcher,
                                    args,
                                    additionalProgressListeners,
                                    stdoutLineHandler,
                                    stderrLineHandler,
                                    progress,
                                    buildId,
                                    runningBuild,
                                    invoker
                                )
                            )
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.failure(e)
            }

            runningBuild.finish(exception = outcome.exceptionOrNull() as? org.gradle.tooling.GradleConnectionException) {
                buildManager.storeResult(it)
            }
            outcome
        }
        return runningBuild to deferred
    }

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
        val (running, deferred) = startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progress = progress,
            launcherProvider = { it.model(kClass.java) },
            invoker = { it.get() },
            requiresGradleProject = requiresGradleProject,
            projectRootInput = projectRoot
        )
        val value = deferred.await()
        val finished = running.awaitFinished()
        return GradleResult(finished, value)
    }

    override fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter
    ): RunningBuild {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progress = progress,
            launcherProvider = { it.newBuild() },
            invoker = { it.run() },
            projectRootInput = projectRoot
        ).first
    }

    @OptIn(ExperimentalTime::class)
    override fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter
    ): RunningBuild {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progress = progress,
            launcherProvider = { connection ->
                connection.newTestLauncher().apply {
                    testPatterns.forEach { (task, patterns) ->
                        withTestsFor { spec ->
                            spec.forTaskPath(task).includePatterns(patterns)
                        }
                    }
                }
            },
            invoker = { it.run() },
            projectRootInput = projectRoot
        ).first
    }
}
