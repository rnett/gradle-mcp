@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.gradle.build.GradleBuildScan
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.gradle.build.TaskOutcome
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.EnvHelper
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.StatusEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseOperationDescriptor
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.problems.ProblemAggregationEvent
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestFailureResult
import org.gradle.tooling.events.test.TestFileAttachmentMetadataEvent
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestKeyValueMetadataEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.gradle.tooling.events.test.TestSkippedResult
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.events.test.TestSuccessResult
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

interface GradleProvider : AutoCloseable {

    suspend fun <T : Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,

        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)? = null,
        requiresGradleProject: Boolean = true
    ): GradleResult<T>

    fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)? = null
    ): RunningBuild

    fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,

        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)? = null
    ): RunningBuild

    val buildManager: BuildManager
}

class InterceptedSpecialCommandException(message: String) : IllegalStateException(message)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DefaultGradleProvider(
    val config: GradleConfiguration,
    val envProvider: EnvProvider = EnvHelper,
    val initScriptProvider: DefaultInitScriptProvider = DefaultInitScriptProvider(),
    override val buildManager: BuildManager
) : GradleProvider {
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

    private fun connect(projectRoot: Path): ProjectConnection {
        return GradleConnector.newConnector()
            .forProjectDirectory(projectRoot.toFile())
            .connect()
    }

    suspend fun getConnection(projectRoot: Path): ProjectConnection = connect(projectRoot)

    @Suppress("UnstableApiUsage")
    class TestCollector(
        val captureFailedTestOutput: Boolean,
        val captureAllTestOutput: Boolean,
        private val cancellationToken: org.gradle.tooling.CancellationToken? = null
    ) : ProgressListener {
        @Volatile
        var isCancelled: Boolean = false
        private val output = java.util.concurrent.ConcurrentHashMap<String, StringBuffer>()
        private val passed = java.util.Collections.synchronizedList(mutableListOf<Result>())
        private val skipped = java.util.Collections.synchronizedList(mutableListOf<Result>())
        private val failed = java.util.Collections.synchronizedList(mutableListOf<Result>())
        private val cancelled = java.util.Collections.synchronizedList(mutableListOf<Result>())
        private val inProgress = java.util.concurrent.ConcurrentHashMap<String, Long>() // Map of test name to start time
        private val metadata = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, String>>()
        private val attachments = java.util.concurrent.ConcurrentHashMap<String, MutableList<FileAttachment>>()

        data class FileAttachment(val file: Path, val mediaType: String?)

        data class Results(
            val passed: Set<Result>,
            val skipped: Set<Result>,
            val failed: Set<Result>,
            val cancelled: Set<Result>,
            val inProgress: Set<Result>
        )

        data class Result(
            val testName: String,
            val output: String?,
            val duration: Duration,
            val failures: List<Failure>?,
            val metadata: Map<String, String>,
            val attachments: List<FileAttachment>
        )

        private fun TestOperationDescriptor.testName(): String? {
            if (this is JvmTestOperationDescriptor) {
                return (this.className ?: return null) + "." + (this.methodName ?: return null)
            }
            return this.testDisplayName
        }

        override fun statusChanged(event: ProgressEvent) {
            when (event) {
                is TestStartEvent -> {
                    val testName = event.descriptor.testName() ?: return
                    inProgress[testName] = event.eventTime
                }

                is TestFinishEvent -> {
                    val testName = event.descriptor.testName() ?: return
                    val wasInProgress = inProgress.remove(testName) != null
                    val testResult = Result(
                        testName,
                        null,
                        (event.result.endTime - event.result.startTime).milliseconds,
                        null,
                        metadata.remove(testName) ?: emptyMap(),
                        attachments.remove(testName) ?: emptyList()
                    )
                    val output = output.remove(testName)?.toString() ?: ""
                    when (val result = event.result) {
                        is TestSuccessResult -> {
                            passed += testResult.copy(output = output.takeIf { captureAllTestOutput })
                        }

                        is TestSkippedResult -> {
                            val r = testResult.copy(output = output.takeIf { captureAllTestOutput })
                            if (wasInProgress && (isCancelled || cancellationToken?.isCancellationRequested == true)) {
                                cancelled += r
                            } else {
                                skipped += r
                            }
                        }

                        is TestFailureResult -> {
                            failed += testResult.copy(failures = result.failures.toList(), output = output.takeIf { captureAllTestOutput || captureFailedTestOutput })
                        }
                    }
                }

                is TestOutputEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    val prefix = if (event.descriptor.destination == Destination.StdErr) "STDERR: " else ""
                    output.computeIfAbsent(testName) { StringBuffer() }.append(prefix + event.descriptor.message)
                }

                is TestKeyValueMetadataEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    metadata.computeIfAbsent(testName) { java.util.concurrent.ConcurrentHashMap() }.putAll(event.values)
                }

                is TestFileAttachmentMetadataEvent -> {
                    val testName = (event.descriptor.parent as? TestOperationDescriptor)?.testName() ?: return
                    attachments.computeIfAbsent(testName) { java.util.Collections.synchronizedList(mutableListOf()) }.add(FileAttachment(event.file.toPath(), event.mediaType))
                }
            }
        }

        fun results(endTime: Long): Results {
            val inProgressResults = inProgress.map { (name, startTime) ->
                Result(
                    name,
                    output[name]?.toString(),
                    (endTime - startTime).milliseconds,
                    null,
                    metadata[name] ?: emptyMap(),
                    attachments[name] ?: emptyList()
                )
            }.toSet()

            return Results(
                passed = passed.toList().toSet(),
                skipped = skipped.toList().toSet(),
                failed = failed.toList().toSet(),
                cancelled = cancelled.toList().toSet(),
                inProgress = inProgressResults
            )
        }

        val operations = buildSet {
            add(OperationType.TEST)
            if (captureFailedTestOutput || captureAllTestOutput) {
                add(OperationType.TEST_OUTPUT)
                add(OperationType.TEST_METADATA)
            }
        }
    }

    private fun <I : ConfigurableLauncher<*>, R> startBuild(
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)?,
        launcherProvider: suspend (ProjectConnection) -> I,
        invoker: (I) -> R,
        requiresGradleProject: Boolean = true,
        projectRootInput: GradleProjectRoot
    ): Pair<RunningBuild, Deferred<Result<R>>> {
        val projectRoot = GradlePathUtils.getRootProjectPath(projectRootInput, requiresGradleProject)
        val buildId = BuildId.newId()
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
                        progressHandler,
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
                        progressHandler,
                        requiresGradleProject
                    )
                    val text = "Gradle ${model.value.getOrThrow().gradle.gradleVersion}"
                    runningBuild.addLogLine(text)
                    stdoutLineHandler?.invoke(text)
                    Result.failure(InterceptedSpecialCommandException("Version command intercepted, result is in console output."))
                } else {
                    connect(projectRoot).use { connection ->
                        try {
                            val launcher = launcherProvider(connection)
                            Result.success(
                                launcher.invokeBuild(
                                    args,
                                    additionalProgressListeners,
                                    stdoutLineHandler,
                                    stderrLineHandler,
                                    progressHandler,
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
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)?,
        requiresGradleProject: Boolean
    ): GradleResult<T> {
        val (running, deferred) = startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progressHandler = progressHandler,
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
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)?
    ): RunningBuild {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progressHandler = progressHandler,
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
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)?
    ): RunningBuild {
        return startBuild(
            args = args,
            additionalProgressListeners = additionalProgressListeners,
            stdoutLineHandler = stdoutLineHandler,
            stderrLineHandler = stderrLineHandler,
            progressHandler = progressHandler,
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

    private suspend fun <I : ConfigurableLauncher<*>, R> I.invokeBuild(
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)?,
        buildId: BuildId,
        runningBuild: RunningBuild,
        invoker: (I) -> R
    ): R {
        return localSupervisorScope(exceptionHandler = {
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
            val initScripts = initScriptProvider.extractInitScripts(
                args.requestedInitScripts + if (args.publishScan || args.additionalArguments.contains("--scan")) listOf("scans") else emptyList()
            )
            val allArguments = initScripts.flatMap { listOf("-I", it.toString()) } + args.allAdditionalArguments
            withArguments(allArguments)
            setColorOutput(false)

            val cancellationToken = runningBuild.cancellationTokenSource.token()
            withCancellationToken(cancellationToken)

            currentCoroutineContext()[Job]?.invokeOnCompletion {
                if (it is CancellationException)
                    runningBuild.stop()
            }

            addProgressListener(runningBuild.testResultsInternal, runningBuild.testResultsInternal.operations)

            addProgressListener({ event ->
                if (event is ProblemAggregationEvent)
                    runningBuild.problemsAccumulator.add(event.problemAggregation)
                if (event is SingleProblemEvent)
                    runningBuild.problemsAccumulator.add(event.problem)
            }, OperationType.PROBLEMS)

            addProgressListener({ event ->
                if (event is BuildPhaseStartEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is BuildPhaseOperationDescriptor) {
                        runningBuild.onPhaseStart(descriptor.buildPhase, descriptor.buildItemsCount)
                        progressHandler?.invoke(0.0, 1.0, "Starting phase: ${descriptor.buildPhase}")
                    }
                } else if (event is BuildPhaseFinishEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is BuildPhaseOperationDescriptor) {
                        progressHandler?.invoke(1.0, 1.0, "Finished phase: ${descriptor.buildPhase}")
                    }
                }
            }, OperationType.BUILD_PHASE)

            addProgressListener({ event ->
                if (event is TaskStartEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is TaskOperationDescriptor) {
                        val taskPath = descriptor.taskPath
                        val total = runningBuild.totalItems.get()
                        if (total > 0) {
                            val completed = runningBuild.completedItems.get()
                            progressHandler?.invoke(completed.toDouble() / total, 1.0, "Executing task: $taskPath")
                        }
                    }
                } else if (event is ProjectConfigurationStartEvent) {
                    val total = runningBuild.totalItems.get()
                    if (total > 0) {
                        val completed = runningBuild.completedItems.get()
                        progressHandler?.invoke(completed.toDouble() / total, 1.0, "Configuring project: ${event.descriptor.displayName}")
                    }
                } else if (event is TaskFinishEvent) {
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
                        runningBuild.addTaskResult(taskPath, outcome, duration, runningBuild.taskOutputs[taskPath])
                        runningBuild.onItemFinish()

                        val total = runningBuild.totalItems.get()
                        if (total > 0) {
                            val completed = runningBuild.completedItems.get()
                            progressHandler?.invoke(completed.toDouble() / total, 1.0, "Finished task: $taskPath")
                        }
                    } else {
                        runningBuild.addTaskCompleted(event.descriptor.displayName)
                    }
                } else if (event is ProjectConfigurationFinishEvent) {
                    runningBuild.onItemFinish()

                    val total = runningBuild.totalItems.get()
                    if (total > 0) {
                        val completed = runningBuild.completedItems.get()
                        progressHandler?.invoke(completed.toDouble() / total, 1.0, "Finished configuration: ${event.descriptor.displayName}")
                    }
                }
            }, OperationType.TASK, OperationType.PROJECT_CONFIGURATION)

            addProgressListener({ event ->
                if (event is StatusEvent) {
                    val total = runningBuild.totalItems.get()
                    if (total > 0) {
                        val completed = runningBuild.completedItems.get()
                        val currentProgress = if (event.total > 0) event.progress.toDouble() / event.total else 0.0
                        val phaseProgress = (completed + currentProgress) / total
                        progressHandler?.invoke(phaseProgress, 1.0, event.descriptor.displayName)
                    } else {
                        progressHandler?.invoke(0.0, null, event.descriptor.displayName)
                    }
                }
            }, OperationType.GENERIC)

            additionalProgressListeners.forEach {
                addProgressListener(it.key, it.value)
            }

            setStandardOutput(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 8192
                    writer = object : GradleStdoutWriter({
                        if (it.contains("Failed to set up gradle-mcp output capturing", ignoreCase = true)) {
                            runningBuild.taskOutputCapturingFailed = true
                        }
                        processTaskOutput(it, runningBuild, false, buildId, stdoutLineHandler)
                    }) {
                        override fun onScanPublication(url: String) {
                            runningBuild.publishedScansInternal += GradleBuildScan.fromUrl(url)
                        }
                    }
                }.get().buffered()
            )

            setStandardError(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 8192
                    writer = LineEmittingWriter {
                        processTaskOutput(it, runningBuild, true, buildId, stderrLineHandler)
                    }
                }.get().buffered()
            )

            val result = scope.async {
                LOGGER.info("Starting Gradle build (buildId={}, args={})", buildId, allArguments)
                invoker(this@invokeBuild)
            }.await()
            LOGGER.info("Gradle build finished (buildId={})", buildId)
            result
        }
    }

    private val taskOutputRegex = Regex("\\[gradle-mcp] \\[(.+)] \\[(.+)]: (.*)")

    private fun processTaskOutput(
        line: String,
        runningBuild: RunningBuild,
        isError: Boolean,
        buildId: BuildId,
        lineHandler: ((String) -> Unit)?
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
            val logLine = if (isError) "STDERR: $line" else line
            runningBuild.addLogLine(logLine)
            lineHandler?.invoke(line)
            LOGGER.makeLoggingEventBuilder(Level.INFO)
                .addKeyValue("buildId", buildId)
                .log("Build ${if (isError) "stderr" else "stdout"}: $line")
        }
    }
}
