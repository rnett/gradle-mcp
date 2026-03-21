@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleStdoutWriter
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.ConfigurableLauncher
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationType
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
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestStartEvent
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface BuildExecutionService {
    suspend fun <I : ConfigurableLauncher<*>, R> invokeBuild(
        launcher: I,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter,
        buildId: BuildId,
        runningBuild: RunningBuild,
        invoker: (I) -> R
    ): R
}

class DefaultBuildExecutionService(
    private val envProvider: EnvProvider,
    private val initScriptProvider: DefaultInitScriptProvider = DefaultInitScriptProvider()
) : BuildExecutionService {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultBuildExecutionService::class.java)
        private val TASK_OUTPUT_REGEX = Regex("""\[gradle-mcp] \[(.+)] \[(.+)]: (.*)""")

        private fun findTaskPath(descriptor: OperationDescriptor?): String? {
            var current = descriptor
            while (current != null) {
                if (current is TaskOperationDescriptor) {
                    return current.taskPath
                }
                current = current.parent
            }
            return null
        }
    }

    override suspend fun <I : ConfigurableLauncher<*>, R> invokeBuild(
        launcher: I,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter,
        buildId: BuildId,
        runningBuild: RunningBuild,
        invoker: (I) -> R
    ): R {
        return localSupervisorScope(exceptionHandler = {
            LOGGER.makeLoggingEventBuilder(Level.WARN)
                .addKeyValue("buildId", buildId)
                .log("Error during job launched during build", it)
        }) { scope ->
            configureLauncher(launcher, args, buildId, runningBuild)

            registerProgressListeners(launcher, runningBuild, additionalProgressListeners)

            setupOutputRedirection(launcher, runningBuild, buildId, stdoutLineHandler, stderrLineHandler)

            // Collect progress from the tracker and report it
            scope.launch {
                runningBuild.progressTracker.progress.collect { p ->
                    progress.report(p.progress, 1.0, p.message)
                }
            }

            val result = scope.async {
                LOGGER.info("Starting Gradle build (buildId={}, args={})", buildId, args.allAdditionalArguments)
                withContext(Dispatchers.IO) {
                    invoker(launcher)
                }
            }.await()
            LOGGER.info("Gradle build finished (buildId={})", buildId)
            result
        }
    }

    private suspend fun configureLauncher(
        launcher: ConfigurableLauncher<*>,
        args: GradleInvocationArguments,
        buildId: BuildId,
        runningBuild: RunningBuild
    ) {
        val env = args.actualEnvVars(envProvider)
        LOGGER.info("Setting environment variables for build: ${env.keys}")
        launcher.setEnvironmentVariables(env)

        @Suppress("UNCHECKED_CAST")
        launcher.withSystemProperties(args.additionalSystemProps)
        launcher.addJvmArguments(args.additionalJvmArgs + "-Dscan.tag.MCP")
        launcher.withDetailedFailure()

        if (args.javaHome != null) {
            val file = java.io.File(args.javaHome)
            if (file.exists() && file.isDirectory) {
                launcher.setJavaHome(file)
            } else {
                LOGGER.warn("Specified javaHome does not exist or is not a directory: ${args.javaHome}")
            }
        }

        val initScripts = initScriptProvider.extractInitScripts(
            args.requestedInitScripts + if (args.publishScan || args.additionalArguments.contains("--scan")) listOf("scans") else emptyList()
        )
        val allArguments = initScripts.flatMap { listOf("-I", it.toString()) } + args.allAdditionalArguments
        launcher.withArguments(allArguments)
        launcher.setColorOutput(false)

        val cancellationToken = runningBuild.cancellationTokenSource.token()
        launcher.withCancellationToken(cancellationToken)

        currentCoroutineContext()[Job]?.invokeOnCompletion {
            if (it is CancellationException)
                runningBuild.stop()
        }
    }

    private fun registerProgressListeners(
        launcher: ConfigurableLauncher<*>,
        runningBuild: RunningBuild,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>
    ) {
        launcher.addProgressListener(runningBuild.testResultsInternal, runningBuild.testResultsInternal.operations)

        // Test progress
        launcher.addProgressListener({ event ->
            if (event is TestStartEvent || event is TestFinishEvent) {
                runningBuild.progressTracker.emitProgress()
            }
        }, OperationType.TEST)

        // Problems aggregation
        launcher.addProgressListener({ event ->
            if (event is ProblemAggregationEvent)
                runningBuild.problemsAccumulator.add(event.problemAggregation)
            if (event is SingleProblemEvent)
                runningBuild.problemsAccumulator.add(event.problem)
        }, OperationType.PROBLEMS)

        // Build phase tracking
        launcher.addProgressListener({ event ->
            if (event is BuildPhaseStartEvent) {
                val descriptor = event.descriptor
                if (descriptor is BuildPhaseOperationDescriptor) {
                    runningBuild.progressTracker.onPhaseStart(descriptor.buildPhase, descriptor.buildItemsCount)
                }
            } else if (event is BuildPhaseFinishEvent) {
                val descriptor = event.descriptor
                if (descriptor is BuildPhaseOperationDescriptor) {
                    runningBuild.progressTracker.onPhaseFinish(descriptor.buildPhase)
                }
            }
        }, OperationType.BUILD_PHASE)

        // Task and Configuration tracking
        launcher.addProgressListener({ event ->
            when (event) {
                is TaskStartEvent -> {
                    val descriptor = event.descriptor
                    if (descriptor is TaskOperationDescriptor) {
                        runningBuild.progressTracker.addActiveOperation(descriptor.taskPath)
                    }
                }

                is ProjectConfigurationStartEvent -> {
                    runningBuild.progressTracker.onPhaseStart("CONFIGURATION", 0)
                    runningBuild.progressTracker.addActiveOperation(event.descriptor.displayName)
                }

                is TaskFinishEvent -> handleTaskFinish(event, runningBuild)
                is ProjectConfigurationFinishEvent -> {
                    runningBuild.progressTracker.removeActiveOperation(event.descriptor.displayName)
                    runningBuild.progressTracker.onItemFinish()
                }
            }
        }, OperationType.TASK, OperationType.PROJECT_CONFIGURATION)

        // Generic status events
        launcher.addProgressListener({ event ->
            if (event is StatusEvent) {
                val currentProgress = if (event.total > 0) event.progress.toDouble() / event.total else null
                val taskPath = findTaskPath(event.descriptor)
                runningBuild.progressTracker.setSubStatus(event.descriptor.displayName, currentProgress, taskPath)
            }
        }, OperationType.GENERIC)

        additionalProgressListeners.forEach { (listener, types) ->
            launcher.addProgressListener(listener, types)
        }
    }

    private fun handleTaskFinish(event: TaskFinishEvent, runningBuild: RunningBuild) {
        val descriptor = event.descriptor
        if (descriptor is TaskOperationDescriptor) {
            val taskPath = descriptor.taskPath
            runningBuild.progressTracker.removeActiveOperation(taskPath)

            val result = event.result
            val outcome = when (result) {
                is TaskSuccessResult -> when {
                    result.isFromCache -> TaskOutcome.FROM_CACHE
                    result.isUpToDate -> TaskOutcome.UP_TO_DATE
                    else -> TaskOutcome.SUCCESS
                }

                is TaskFailureResult -> TaskOutcome.FAILED
                is TaskSkippedResult -> if (result.skipMessage == "NO-SOURCE") TaskOutcome.NO_SOURCE else TaskOutcome.SKIPPED
                else -> TaskOutcome.SUCCESS
            }

            val duration = if (result.startTime > 0) (event.eventTime - result.startTime).milliseconds else 0.seconds
            runningBuild.addTaskResult(taskPath, outcome, duration, runningBuild.taskOutputs[taskPath])
            runningBuild.progressTracker.onItemFinish()
        } else {
            runningBuild.addTaskCompleted(event.descriptor.displayName)
        }
    }

    private fun setupOutputRedirection(
        launcher: ConfigurableLauncher<*>,
        runningBuild: RunningBuild,
        buildId: BuildId,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?
    ) {
        launcher.setStandardOutput(
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

        launcher.setStandardError(
            WriterOutputStream.builder().apply {
                charset = StandardCharsets.UTF_8
                bufferSize = 8192
                writer = dev.rnett.gradle.mcp.gradle.LineEmittingWriter {
                    processTaskOutput(it, runningBuild, true, buildId, stderrLineHandler)
                }
            }.get().buffered()
        )
    }

    private fun processTaskOutput(
        line: String,
        runningBuild: RunningBuild,
        isError: Boolean,
        buildId: BuildId,
        lineHandler: ((String) -> Unit)?
    ) {
        val taskMatch = TASK_OUTPUT_REGEX.matchEntire(line)
        if (taskMatch != null) {
            val taskPath = taskMatch.groupValues[1]
            val category = taskMatch.groupValues[2]
            val text = taskMatch.groupValues[3]

            if (taskPath == "PROGRESS") {
                runningBuild.progressTracker.handleProgressLine(category, text)
            } else {
                val type = if (category == "system.err") "ERR" else "OUT"
                val formattedLine = "$taskPath $type $text"

                runningBuild.addTaskOutput(taskPath, text)
                runningBuild.replaceLastLogLine(text, formattedLine)
            }
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
