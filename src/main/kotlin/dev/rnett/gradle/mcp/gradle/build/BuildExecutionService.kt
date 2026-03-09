@file:Suppress("UnstableApiUsage")

package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.gradle.BuildId
import dev.rnett.gradle.mcp.gradle.DefaultInitScriptProvider
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.ConfigurableLauncher
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
            val env = args.actualEnvVars(envProvider)
            LOGGER.info("Setting environment variables for build: ${env.keys}")
            launcher.setEnvironmentVariables(env)
            @Suppress("UNCHECKED_CAST")
            launcher.withSystemProperties(args.additionalSystemProps)
            launcher.addJvmArguments(args.additionalJvmArgs + "-Dscan.tag.MCP")
            launcher.withDetailedFailure()
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

            fun emitProgress(isFinish: Boolean) {
                val total = runningBuild.totalItems.get()
                val message = runningBuild.getProgressMessage()
                if (total > 0) {
                    val completed = runningBuild.completedItems.get()
                    progress.report(completed.toDouble() / total, 1.0, message)
                } else {
                    progress.report(if (isFinish) 1.0 else 0.0, 1.0, message)
                }
            }

            launcher.addProgressListener(runningBuild.testResultsInternal, runningBuild.testResultsInternal.operations)

            launcher.addProgressListener({ event ->
                if (event is TestStartEvent || event is TestFinishEvent) {
                    emitProgress(false)
                }
            }, OperationType.TEST)

            launcher.addProgressListener({ event ->
                if (event is ProblemAggregationEvent)
                    runningBuild.problemsAccumulator.add(event.problemAggregation)
                if (event is SingleProblemEvent)
                    runningBuild.problemsAccumulator.add(event.problem)
            }, OperationType.PROBLEMS)

            launcher.addProgressListener({ event ->
                if (event is BuildPhaseStartEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is BuildPhaseOperationDescriptor) {
                        runningBuild.onPhaseStart(descriptor.buildPhase, descriptor.buildItemsCount)
                        emitProgress(false)
                    }
                } else if (event is BuildPhaseFinishEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is BuildPhaseOperationDescriptor) {
                        emitProgress(true)
                    }
                }
            }, OperationType.BUILD_PHASE)

            launcher.addProgressListener({ event ->
                if (event is TaskStartEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is TaskOperationDescriptor) {
                        val taskPath = descriptor.taskPath
                        runningBuild.addActiveOperation(taskPath)
                        emitProgress(false)
                    }
                } else if (event is ProjectConfigurationStartEvent) {
                    runningBuild.onPhaseStart("CONFIGURATION", 0)
                    val operation = event.descriptor.displayName
                    runningBuild.addActiveOperation(operation)
                    emitProgress(false)
                } else if (event is TaskFinishEvent) {
                    val descriptor = event.descriptor
                    if (descriptor is TaskOperationDescriptor) {
                        val taskPath = descriptor.taskPath
                        runningBuild.removeActiveOperation(taskPath)
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
                        emitProgress(true)
                    } else {
                        runningBuild.addTaskCompleted(event.descriptor.displayName)
                    }
                } else if (event is ProjectConfigurationFinishEvent) {
                    val operation = event.descriptor.displayName
                    runningBuild.removeActiveOperation(operation)
                    runningBuild.onItemFinish()
                    emitProgress(true)
                }
            }, OperationType.TASK, OperationType.PROJECT_CONFIGURATION)

            launcher.addProgressListener({ event ->
                if (event is StatusEvent) {
                    val currentProgress = if (event.total > 0) event.progress.toDouble() / event.total else null
                    runningBuild.setSubStatus(event.descriptor.displayName, currentProgress)
                    val total = runningBuild.totalItems.get()
                    val message = runningBuild.getProgressMessage()
                    if (total > 0) {
                        val completed = runningBuild.completedItems.get()
                        val progressValue = (completed + (currentProgress ?: 0.0)) / total
                        progress.report(progressValue, 1.0, message)
                    } else {
                        progress.report(0.0, null, message)
                    }
                }
            }, OperationType.GENERIC)

            additionalProgressListeners.forEach {
                launcher.addProgressListener(it.key, it.value)
            }

            launcher.setStandardOutput(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 8192
                    writer = object : dev.rnett.gradle.mcp.gradle.GradleStdoutWriter({
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

            val result = scope.async {
                LOGGER.info("Starting Gradle build (buildId={}, args={})", buildId, allArguments)
                invoker(launcher)
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

            if (taskPath == "PROGRESS") {
                runningBuild.handleProgressLine(category, text)
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
