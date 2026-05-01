@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleStdoutWriter
import dev.rnett.gradle.mcp.gradle.LineEmittingWriter
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

            val outputRedirection = setupOutputRedirection(launcher, runningBuild, buildId, stdoutLineHandler, stderrLineHandler)

            // Collect progress from the tracker and report it
            scope.launch {
                runningBuild.progressTracker.progress.collect { p ->
                    progress.report(p.progress, 1.0, p.message)
                }
            }

            val result = scope.async {
                LOGGER.info("Starting Gradle build (buildId={}, args={})", buildId, args.allAdditionalArguments)
                withContext(Dispatchers.IO) {
                    try {
                        invoker(launcher)
                    } finally {
                        LOGGER.info("Gradle build finished (buildId={})", buildId)
                        try {
                            outputRedirection.stdout.flush()
                            outputRedirection.stdout.close()
                        } catch (e: Exception) {
                            LOGGER.warn("Error closing stdout for build $buildId", e)
                        }
                        try {
                            outputRedirection.stderr.flush()
                            outputRedirection.stderr.close()
                        } catch (e: Exception) {
                            LOGGER.warn("Error closing stderr for build $buildId", e)
                        }
                        // Flush any pending raw stdout line that wasn't superseded by a
                        // structured [gradle-mcp] [task-output] replacement.
                        outputRedirection.flushPendingStdout()
                    }
                }
            }.await()
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
        launcher.addJvmArguments(args.additionalJvmArgs + "-Dscan.tag.MCP")
        launcher.withDetailedFailure()

        val resolvedJavaHome = args.javaHome ?: env["JAVA_HOME"]

        if (resolvedJavaHome != null) {
            val file = java.io.File(resolvedJavaHome)
            if (file.exists() && file.isDirectory) {
                launcher.setJavaHome(file)
            } else {
                val source = if (args.javaHome != null) "Specified" else "Environment (JAVA_HOME)"
                LOGGER.warn("$source javaHome does not exist or is not a directory: $resolvedJavaHome")
            }
        }

        val initScripts = initScriptProvider.extractInitScripts(
            args.requestedInitScripts +
                    (if (args.publishScan || args.additionalArguments.contains("--scan")) listOf("scans") else emptyList())
        )
        val allArguments = initScripts.flatMap { listOf("-I", it.toString()) } + args.allAdditionalArguments
        launcher.withArguments(allArguments)

        launcher.withSystemProperties(args.additionalSystemProps)
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
                    result.isFromCache -> BuildComponentOutcome.FROM_CACHE
                    result.isUpToDate -> BuildComponentOutcome.UP_TO_DATE
                    else -> BuildComponentOutcome.SUCCESS
                }

                is TaskFailureResult -> BuildComponentOutcome.FAILED
                is TaskSkippedResult -> if (result.skipMessage == "NO-SOURCE") BuildComponentOutcome.NO_SOURCE else BuildComponentOutcome.SKIPPED
                else -> BuildComponentOutcome.SUCCESS
            }

            val duration = if (result.startTime > 0) (event.eventTime - result.startTime).milliseconds else 0.seconds
            runningBuild.addTaskResult(taskPath, outcome, duration, runningBuild.taskOutputs[taskPath])
            runningBuild.progressTracker.onItemFinish()
        } else {
            runningBuild.addTaskCompleted(event.descriptor.displayName)
        }
    }

    /**
     * Data class returned by [setupOutputRedirection] so that the caller can flush any
     * buffered stdout line after the streams are closed.
     */
    private class OutputRedirection(
        val stdout: java.io.OutputStream,
        val stderr: java.io.OutputStream,
        val flushPendingStdout: () -> Unit
    )

    private fun setupOutputRedirection(
        launcher: ConfigurableLauncher<*>,
        runningBuild: RunningBuild,
        buildId: BuildId,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?
    ): OutputRedirection {
        // Pending raw stdout line.  When a non-[gradle-mcp] line arrives on stdout we hold it
        // here instead of adding it to logBuffer immediately.  If the next stdout line is a
        // structured [gradle-mcp] [task-output] replacement, we discard the pending raw line and
        // add the formatted version.  Otherwise we flush the pending line to logBuffer first.
        // This eliminates the need for the unsafe compound replaceLastLogLine operation.
        // The variable is only accessed from the stdout LineEmittingWriter callback chain, which
        // is thread-confined to a single Gradle Tooling API output delivery thread (see
        // LineEmittingWriter's thread-confinement contract), so no locking is needed.
        var pendingRawLine: String? = null

        fun flushPending() {
            pendingRawLine?.let { pending ->
                runningBuild.addLogLine(pending)
                pendingRawLine = null
            }
        }

        val stdoutWriterStream = WriterOutputStream.builder().apply {
            charset = StandardCharsets.UTF_8
            bufferSize = 8192
            writer = object : GradleStdoutWriter({
                if (it.contains("Failed to set up gradle-mcp output capturing", ignoreCase = true)) {
                    runningBuild.taskOutputCapturingFailed = true
                }
                processStdoutLine(it, runningBuild, buildId, stdoutLineHandler, ::flushPending, { pendingRawLine }, { pendingRawLine = it })
            }) {
                override fun onScanPublication(url: String) {
                    runningBuild.publishedScansInternal += GradleBuildScan.fromUrl(url)
                }
            }
        }.get()

        launcher.setStandardOutput(stdoutWriterStream)

        val stderrWriterStream = WriterOutputStream.builder().apply {
            charset = StandardCharsets.UTF_8
            bufferSize = 8192
            writer = LineEmittingWriter {
                processStderrLine(it, runningBuild, buildId, stderrLineHandler)
            }
        }.get()

        launcher.setStandardError(stderrWriterStream)

        return OutputRedirection(stdoutWriterStream, stderrWriterStream, ::flushPending)
    }

    private fun processStdoutLine(
        line: String,
        runningBuild: RunningBuild,
        buildId: BuildId,
        lineHandler: ((String) -> Unit)?,
        flushPending: () -> Unit,
        getPending: () -> String?,
        setPending: (String?) -> Unit
    ) {
        val marker = "[gradle-mcp]"
        if (line.startsWith(marker)) {
            val remaining = line.substringAfter(marker).trim()
            val category = remaining.substringBefore("]").removePrefix("[").trim()
            val content = remaining.substringAfter("]").trim()

            when (category) {
                "PROGRESS" -> {
                    flushPending()
                    val subCategory = content.substringBefore(":").removePrefix("[").removeSuffix("]").trim()
                    val text = content.substringAfter(":").trim()
                    runningBuild.progressTracker.handleProgressLine(subCategory, text)
                }

                "task-output" -> {
                    // The pending raw line is the un-prefixed version of this task output.
                    // Discard it and add the formatted version instead.
                    setPending(null)

                    val taskPath = content.substringBefore("]").removePrefix("[").trim()
                    val remainingAfterTaskPath = content.substringAfter("]").trim()
                    val taskCategory = remainingAfterTaskPath.substringBefore("]").removePrefix("[").trim()
                    val text = remainingAfterTaskPath.substringAfter("]:").trim()

                    val type = if (taskCategory == "system.err") "ERR" else "OUT"
                    val formattedLine = "$taskPath $type $text"

                    runningBuild.addTaskOutput(taskPath, text)
                    runningBuild.addLogLine(formattedLine)
                }

                else -> {
                    flushPending()
                    runningBuild.addLogLine(line)
                }
            }
        } else {
            // Non-structured stdout line.  Hold it as pending — if the next line is a
            // [gradle-mcp] [task-output] that supersedes it, we'll discard it; otherwise
            // we'll flush it to logBuffer when the next line arrives.
            flushPending()
            setPending(line)
            lineHandler?.invoke(line)
            LOGGER.makeLoggingEventBuilder(Level.INFO)
                .addKeyValue("buildId", buildId)
                .log("Build stdout: $line")
        }
    }

    private fun processStderrLine(
        line: String,
        runningBuild: RunningBuild,
        buildId: BuildId,
        lineHandler: ((String) -> Unit)?
    ) {
        val logLine = "STDERR: $line"
        runningBuild.addLogLine(logLine)
        lineHandler?.invoke(line)
        LOGGER.makeLoggingEventBuilder(Level.INFO)
            .addKeyValue("buildId", buildId)
            .log("Build stderr: $line")
    }
}
