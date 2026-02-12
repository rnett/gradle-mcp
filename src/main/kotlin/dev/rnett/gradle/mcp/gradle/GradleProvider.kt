package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.EnvHelper
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemAggregationEvent
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
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
import org.gradle.tooling.model.build.BuildEnvironment
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

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

    val backgroundBuildManager: BackgroundBuildManager
    val buildResults: BuildResults
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultGradleProvider(
    val config: GradleConfiguration,
    val envProvider: EnvProvider = EnvHelper,
    val initScriptProvider: DefaultInitScriptProvider = DefaultInitScriptProvider(),
    override val backgroundBuildManager: BackgroundBuildManager = BackgroundBuildManager(),
    override val buildResults: BuildResults = BuildResults(backgroundBuildManager)
) : GradleProvider {


    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultGradleProvider::class.java)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun close() {
        backgroundBuildManager.listBuilds().forEach { it.stop() }
        connectionCache.synchronous().asMap().values.forEach { it.close() }
        scope.cancel()
    }

    private val connectionCache = Caffeine.newBuilder()
        .expireAfterAccess(config.ttl.toJavaDuration())
        .maximumSize(config.maxConnections.toLong())
        .buildAsync<Path, ProjectConnection> { path, executor ->
            CompletableFuture.supplyAsync({
                GradleConnector.newConnector()
                    .forProjectDirectory(path.toFile())
                    .connect()
            }, executor)
        }

    suspend fun getConnection(projectRoot: Path): ProjectConnection {
        var connection = connectionCache.get(projectRoot).asDeferred().await()
        if (!connection.isAlive()) {
            connectionCache.synchronous().invalidate(projectRoot)
            connection = connectionCache.get(projectRoot).asDeferred().await()
        }
        return connection
    }

    private fun ProjectConnection.isAlive(): Boolean {
        return try {
            this.model(BuildEnvironment::class.java).get()
            true
        } catch (_: Throwable) {
            false
        }
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
        projectRootInput: GradleProjectRoot,
    ): RunningBuild<R> {
        val projectRoot = GradlePathUtils.getRootProjectPath(projectRootInput, requiresGradleProject)
        val buildId = BuildId.newId()
        val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
        val runningBuild = RunningBuild<R>(
            id = buildId,
            args = args,
            startTime = buildId.timestamp,
            projectRoot = projectRoot,
            cancellationTokenSource = cancellationTokenSource,
            backgroundBuildManager = backgroundBuildManager
        ).also { backgroundBuildManager.registerBuild(it) }

        scope.launch {
            try {
                val connection = getConnection(projectRoot)
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
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
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
            projectRootInput = projectRoot
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
            projectRootInput = projectRoot
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
            projectRootInput = projectRoot
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

            cleanupConnectionIfOld(connection, runningBuild.projectRoot, args)
        }) { scope ->
            val env = args.actualEnvVars(envProvider)
            LOGGER.info("Setting environment variables for build: ${env.keys}")
            setEnvironmentVariables(env)
            @Suppress("UNCHECKED_CAST")
            withSystemProperties(args.additionalSystemProps)
            addJvmArguments(args.additionalJvmArgs + "-Dscan.tag.MCP")
            withDetailedFailure()
            val initScripts = initScriptProvider.extractInitScripts(args.requestedInitScripts)
            val allArguments = initScripts.flatMap { listOf("-I", it.toString()) } + args.allAdditionalArguments
            withArguments(allArguments)
            setColorOutput(false)

            val cancellationToken = runningBuild.cancellationTokenSource.token()
            withCancellationToken(cancellationToken)

            currentCoroutineContext()[Job]?.invokeOnCompletion {
                if (it is CancellationException)
                    runningBuild.stop()
            }

            additionalProgressListeners.forEach {
                addProgressListener(it.key, it.value)
            }

            addProgressListener(runningBuild.testResultsInternal, runningBuild.testResultsInternal.operations)

            addProgressListener(object : ProgressListener {
                override fun statusChanged(event: ProgressEvent) {
                    if (event is ProblemAggregationEvent)
                        runningBuild.problemsAccumulator.add(event.problemAggregation)
                    if (event is SingleProblemEvent)
                        runningBuild.problemsAccumulator.add(event.problem)
                }
            }, OperationType.PROBLEMS)

            addProgressListener({ event ->
                if (event is TaskFinishEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is TaskOperationDescriptor) {
                        val taskPath = descriptor.taskPath
                        val result = event.result
                        val outcome = when (result) {
                            is TaskSuccessResult -> {
                                when {
                                    result.isFromCache -> TaskOutcome.FROM_CACHE
                                    result.isUpToDate -> TaskOutcome.UP_TO_DATE
                                    else -> TaskOutcome.SUCCESS
                                }
                            }

                            is TaskFailureResult -> TaskOutcome.FAILED
                            is TaskSkippedResult -> {
                                if (result.skipMessage == "NO-SOURCE") TaskOutcome.NO_SOURCE
                                else TaskOutcome.SKIPPED
                            }

                            else -> TaskOutcome.SUCCESS
                        }
                        val startTime = result.startTime
                        val endTime = event.eventTime
                        val duration = if (startTime > 0) (endTime - startTime).milliseconds else 0.seconds
                        runningBuild.addTaskResult(taskPath, outcome, duration, runningBuild.taskOutputs[taskPath]?.toString())
                    } else {
                        runningBuild.addTaskCompleted(event.descriptor.displayName)
                    }
                }
            }, OperationType.TASK)

            // Build scan TOS acceptance

            val tosHolder = CompletableDeferred<Deferred<Boolean>>()
            val inputStream = DeferredInputStream(GradleScanTosAcceptRequest.TIMEOUT + 30.seconds, scope.async(start = CoroutineStart.LAZY) {
                try {
                    val query = withTimeout(3.seconds) {
                        tosHolder.await()
                    }

                    ByteArrayInputStream(
                        (if (query.await()) "yes\n" else "no\n").encodeToByteArray()
                    )
                } catch (e: Exception) {
                    LOGGER.debug("Error or timeout waiting for ToS holder", e)
                    ByteArrayInputStream(byteArrayOf())
                }
            })

            setStandardOutput(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = object : GradleStdoutWriter(config.allowPublicScansPublishing, {
                        if (it.contains("Failed to set up gradle-mcp output capturing", ignoreCase = true)) {
                            runningBuild.taskOutputCapturingFailed = true
                        }
                        processTaskOutput(it, runningBuild, false, buildId, stdoutLineHandler ?: {})
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
                            runningBuild.publishedScans += GradleBuildScan.fromUrl(url)
                        }

                    }
                }.get()
            )

            setStandardError(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = LineEmittingWriter {
                        processTaskOutput(it, runningBuild, true, buildId, stderrLineHandler ?: {})
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

            val result = GradleResult(
                runningBuild.toResult(exception),
                outcome as Result<R>
            )
            runningBuild.updateStatus(result)
            buildResults.storeResult(result.buildResult as BuildResult)

            cleanupConnectionIfOld(connection, runningBuild.projectRoot, args)
        }
    }

    private fun cleanupConnectionIfOld(connection: ProjectConnection, projectRoot: Path, args: GradleInvocationArguments) {
        val currentConnection = connectionCache.getIfPresent(projectRoot)?.asDeferred()
        if (currentConnection?.isCompleted != true) return

        if (currentConnection.getCompleted() !== connection) {
            try {
                connection.close()
            } catch (e: Exception) {
                LOGGER.error("Error closing Gradle connection", e)
            }
        }
    }

    private val taskOutputRegex = Regex("\\[gradle-mcp] \\[(.+)] \\[(.+)]: (.*)")

    private inline fun processTaskOutput(
        line: String,
        runningBuild: RunningBuild<*>,
        isError: Boolean,
        buildId: BuildId,
        lineHandler: (String) -> Unit
    ) {
        val taskMatch = taskOutputRegex.matchEntire(line)
        if (taskMatch != null) {
            val taskPath = taskMatch.groupValues[1]
            val category = taskMatch.groupValues[2]
            val text = taskMatch.groupValues[3]
            val type = if (category == "system.err") "ERR" else "OUT"
            val formattedLine = "$taskPath $type $text"

            runningBuild.addTaskOutput(taskPath, text)
            runningBuild.replaceLastLogLine(text, formattedLine)
        } else {
            val logLine = if (isError) "ERR: $line" else line
            runningBuild.addLogLine(logLine)
            lineHandler.invoke(line)
            LOGGER.makeLoggingEventBuilder(Level.INFO)
                .addKeyValue("buildId", buildId)
                .log("Build ${if (isError) "stderr" else "stdout"}: $line")
        }
    }
}