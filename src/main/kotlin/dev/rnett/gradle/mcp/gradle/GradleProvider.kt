package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.EnvHelper
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemAggregationEvent
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.events.test.TestSkippedResult
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.events.test.TestSuccessResult
import org.gradle.tooling.model.Model
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaDuration

@Serializable
enum class BuildStatus {
    RUNNING, SUCCESSFUL, FAILED, CANCELLED
}

data class RunningBuild<T>(
    val id: BuildId,
    val args: GradleInvocationArguments,
    val startTime: Instant,
    val logBuffer: StringBuffer = StringBuffer(),
    @Transient val cancellationTokenSource: CancellationTokenSource,
    val result: CompletableDeferred<GradleResult<T>> = CompletableDeferred()
) {
    val problems = ProblemsAccumulator()
    val testResults = DefaultGradleProvider.TestCollector(true, true)
    val scans = ConcurrentLinkedQueue<GradleBuildScan>()

    private val _logLines = MutableSharedFlow<String>(replay = 1)
    val logLines: SharedFlow<String> = _logLines.asSharedFlow()

    private val _completedTasks = MutableSharedFlow<String>(replay = 1)
    val completedTasks: SharedFlow<String> = _completedTasks.asSharedFlow()

    private val _taskPaths: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val completedTaskPaths: Set<String> get() = _taskPaths.toSet()

    var status: BuildStatus = BuildStatus.RUNNING
        private set
    var endTime: Instant? = null
        private set

    val consoleOutput: CharSequence get() = logBuffer

    suspend fun awaitFinished(): GradleResult<T> = result.await()

    fun stop() {
        cancellationTokenSource.cancel()
    }

    internal fun updateStatus(gradleResult: GradleResult<T>? = null, exception: Throwable? = null) {
        if (gradleResult != null) {
            val finalStatus = when {
                gradleResult.value.exceptionOrNull() is BuildCancelledException -> BuildStatus.CANCELLED
                gradleResult.buildResult.isSuccessful -> BuildStatus.SUCCESSFUL
                else -> BuildStatus.FAILED
            }
            this.status = finalStatus
            this.result.complete(gradleResult)
        } else if (exception != null) {
            val finalStatus = if (exception is BuildCancelledException) BuildStatus.CANCELLED else BuildStatus.FAILED
            this.status = finalStatus
            this.result.completeExceptionally(exception)
        }

        if (this.status != BuildStatus.RUNNING) {
            this.endTime = kotlin.time.Clock.System.now()
            BackgroundBuildManager.removeBuild(id)
        }
    }

    internal fun addLogLine(line: String) {
        logBuffer.appendLine(line)
        _logLines.tryEmit(line)
    }

    internal fun addTaskCompleted(taskPath: String) {
        _taskPaths.add(taskPath)
        _completedTasks.tryEmit(taskPath)
    }
}

interface GradleProvider : AutoCloseable {

    fun <T : Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        requiresGradleProject: Boolean = true
    ): RunningBuild<T>

    fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): RunningBuild<Unit>

    fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): RunningBuild<Unit>
}

class DefaultGradleProvider(
    val config: GradleConfiguration,
    val envProvider: EnvProvider = EnvHelper,
) : GradleProvider {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultGradleProvider::class.java)
    }

    private val scope = GlobalScope + SupervisorJob() + Dispatchers.IO

    override fun close() {
        BackgroundBuildManager.listBuilds().forEach { it.stop() }
        scope.cancel()
    }

    private val connectionCache = Caffeine.newBuilder()
        .expireAfterAccess(config.ttl.toJavaDuration())
        .maximumSize(config.maxConnections.toLong())
        .evictionListener<Path, ProjectConnection> { k, v, _ ->
            v?.close()
        }
        .buildAsync<Path, ProjectConnection> {
            GradleConnector.newConnector().forProjectDirectory(it.toFile()).connect()
        }

    suspend fun getConnection(projectRoot: Path): ProjectConnection {
        return connectionCache.get(projectRoot).asDeferred().await()
    }

    suspend fun validateAndGetConnection(projectRoot: GradleProjectRoot, requiresGradleProject: Boolean = true): ProjectConnection {
        return getConnection(GradlePathUtils.getRootProjectPath(projectRoot, requiresGradleProject))
    }

    class TestCollector(val captureFailedTestOutput: Boolean, val captureAllTestOutput: Boolean) : ProgressListener {
        private val output = mutableMapOf<String, StringBuilder>()
        private val passed = mutableListOf<Result>()
        private val skipped = mutableListOf<Result>()
        private val failed = mutableListOf<Result>()

        data class Results(
            val passed: Set<Result>,
            val skipped: Set<Result>,
            val failed: Set<Result>,
        )

        data class Result(
            val testName: String,
            val output: String?,
            val duration: Duration,
            val failures: List<Failure>?,
        )

        private fun TestOperationDescriptor.testName(): String? {
            if (this is JvmTestOperationDescriptor) {
                return (this.className ?: return null) + "." + (this.methodName ?: return null)
            }
            return this.testDisplayName
        }

        override fun statusChanged(event: ProgressEvent) {
            when (event) {
                is TestStartEvent -> {}
                is TestFinishEvent -> {
                    val testResult = Result(
                        event.descriptor.testName() ?: return,
                        null,
                        (event.result.endTime - event.result.startTime).milliseconds,
                        null
                    )
                    val output = output.remove(testResult.testName)?.toString() ?: ""
                    when (val result = event.result) {
                        is TestSuccessResult -> {
                            passed += testResult.copy(output = output.takeIf { captureAllTestOutput })
                        }

                        is TestSkippedResult -> {
                            skipped += testResult.copy(output = output.takeIf { captureAllTestOutput })
                        }

                        is TestFailureResult -> {
                            failed += testResult.copy(failures = result.failures.toList(), output = output.takeIf { captureAllTestOutput || captureFailedTestOutput })
                        }
                    }
                }

                is TestOutputEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    val prefix = if (event.descriptor.destination == Destination.StdErr) "Err: " else ""
                    output.getOrPut(testName) { StringBuilder() }.append(prefix + event.descriptor.message)
                }
            }
        }

        fun results() = Results(
            passed = passed.toList().toSet(),
            skipped = skipped.toList().toSet(),
            failed = failed.toList().toSet()
        )

        val operations = buildSet {
            add(OperationType.TEST)
            if (captureFailedTestOutput || captureAllTestOutput) {
                add(OperationType.TEST_OUTPUT)
            }
        }
    }

    private fun <I : ConfigurableLauncher<*>, R> startBuild(
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        launcherProvider: suspend (ProjectConnection) -> I,
        invoker: (I) -> R,
        requiresGradleProject: Boolean = true,
        projectRoot: GradleProjectRoot,
    ): RunningBuild<R> {
        val buildId = BuildId.newId()
        val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
        val runningBuild = RunningBuild<R>(
            id = buildId,
            args = args,
            startTime = buildId.timestamp,
            cancellationTokenSource = cancellationTokenSource
        ).also { BackgroundBuildManager.registerBuild(it) }

        scope.launch {
            try {
                val connection = validateAndGetConnection(projectRoot, requiresGradleProject)
                val launcher = launcherProvider(connection)
                launcher.invokeBuild(
                    connection,
                    args,
                    additionalProgressListeners,
                    stdoutLineHandler,
                    stderrLineHandler,
                    tosAccepter,
                    buildId,
                    runningBuild,
                    invoker
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                runningBuild.updateStatus(exception = e)
            }
        }
        return runningBuild
    }

    override fun <T : Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        requiresGradleProject: Boolean
    ): RunningBuild<T> {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            tosAccepter = tosAccepter,
            launcherProvider = { it.model(kClass.java) },
            invoker = { it.get() },
            requiresGradleProject = requiresGradleProject,
            projectRoot = projectRoot
        )
    }

    override fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
    ): RunningBuild<Unit> {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            tosAccepter = tosAccepter,
            launcherProvider = { it.newBuild() },
            invoker = { it.run() },
            projectRoot = projectRoot
        )
    }

    @OptIn(ExperimentalTime::class)
    override fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
    ): RunningBuild<Unit> {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            tosAccepter = tosAccepter,
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
            projectRoot = projectRoot
        )
    }

    private suspend fun <I : ConfigurableLauncher<*>, R> I.invokeBuild(
        connection: ProjectConnection,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        buildId: BuildId,
        runningBuild: RunningBuild<R>,
        invoker: (I) -> R,
    ) {
        localSupervisorScope(exceptionHandler = {
            LOGGER.makeLoggingEventBuilder(Level.WARN)
                .addKeyValue("buildId", buildId)
                .log("Error during job launched during build", it)
        }) { scope ->
            val env = args.actualEnvVars(envProvider)
            LOGGER.info("Setting environment variables for build: ${env.keys}")
            setEnvironmentVariables(env)
            @Suppress("UNCHECKED_CAST")
            withSystemProperties(args.additionalSystemProps)
            addJvmArguments(args.additionalJvmArgs + "-Dscan.tag.MCP")
            withDetailedFailure()
            withArguments(args.allAdditionalArguments)
            setColorOutput(false)

            withCancellationToken(runningBuild.cancellationTokenSource.token())

            currentCoroutineContext()[Job]?.invokeOnCompletion {
                if (it != null)
                    runningBuild.stop()
            }

            additionalProgressListeners.forEach {
                addProgressListener(it.key, it.value)
            }

            addProgressListener(runningBuild.testResults, runningBuild.testResults.operations)

            addProgressListener(object : ProgressListener {
                override fun statusChanged(event: ProgressEvent) {
                    if (event is ProblemAggregationEvent)
                        runningBuild.problems.add(event.problemAggregation)
                    if (event is SingleProblemEvent)
                        runningBuild.problems.add(event.problem)
                }
            }, OperationType.PROBLEMS)

            addProgressListener({ event ->
                if (event is TaskFinishEvent) {
                    runningBuild.addTaskCompleted(event.descriptor.taskPath)
                }
            }, OperationType.TASK)

            // Build scan TOS acceptance

            val tosHolder = CompletableDeferred<Deferred<Boolean>>()
            val inputStream = DeferredInputStream(GradleScanTosAcceptRequest.TIMEOUT + 30.seconds, scope.async(start = CoroutineStart.LAZY) {
                val query = withTimeout(3.seconds) {
                    tosHolder.await()
                }

                ByteArrayInputStream(
                    (if (query.await()) "yes\n" else "no\n").encodeToByteArray()
                )
            })

            setStandardOutput(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = object : GradleStdoutWriter(config.allowPublicScansPublishing, {
                        runningBuild.addLogLine(it)
                        stdoutLineHandler?.invoke(it)
                        LOGGER.makeLoggingEventBuilder(Level.INFO)
                            .addKeyValue("buildId", buildId)
                            .log("Build stdout: $it")

                    }) {
                        override fun onScansTosRequest(tosAcceptRequest: GradleScanTosAcceptRequest) {
                            if (tosHolder.isActive) {
                                tosHolder.complete(
                                    scope.async(Dispatchers.IO) {
                                        runCatchingExceptCancellation { tosAccepter.invoke(tosAcceptRequest) }
                                            .getOrElse {
                                                LOGGER.warn("Error asking for ToS acceptance - assuming 'no'", it)
                                                false
                                            }
                                    }
                                )
                            }
                        }

                        override fun onScanPublication(url: String) {
                            runningBuild.scans += GradleBuildScan.fromUrl(url)
                        }

                    }
                }.get()
            )

            setStandardError(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = LineEmittingWriter {
                        runningBuild.addLogLine("ERR: $it")
                        stderrLineHandler?.invoke(it)
                        LOGGER.makeLoggingEventBuilder(Level.INFO)
                            .addKeyValue("buildId", buildId)
                            .log("Build stderr: $it")
                    }
                }.get()
            )

            setStandardInput(inputStream)

            val outcome = runCatchingExceptCancellation {
                scope.async {
                    invoker(this@invokeBuild)
                }.await()
            }

            val exception = outcome.exceptionOrNull()?.let {
                if (it is BuildException || it is TestExecutionException || it is BuildCancelledException) {
                    it
                } else {
                    throw it
                }
            }

            @Suppress("UNCHECKED_CAST")
            val result = GradleResult.build(
                args,
                buildId,
                runningBuild.consoleOutput,
                runningBuild.scans.toList(),
                runningBuild.problems.aggregate(),
                runningBuild.testResults.results(),
                exception,
                outcome
            )
            BuildResults.storeResult(result.buildResult)
            runningBuild.updateStatus(result)
        }
    }
}