package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import dev.rnett.gradle.mcp.tools.GradlePathUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
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
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

class GradleProvider(
    val config: GradleConfiguration
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(GradleProvider::class.java)
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
            passed = passed.toSet(),
            skipped = skipped.toSet(),
            failed = failed.toSet()
        )

        val operations = buildSet {
            add(OperationType.TEST)
            if (captureFailedTestOutput || captureAllTestOutput) {
                add(OperationType.TEST_OUTPUT)
            }
        }
    }

    private suspend inline fun <I : ConfigurableLauncher<*>, R> I.invokeBuild(
        connection: ProjectConnection,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        noinline stdoutLineHandler: ((String) -> Unit)?,
        noinline stderrLineHandler: ((String) -> Unit)?,
        crossinline tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        crossinline invoker: (I) -> R,
    ): GradleResult<R> = withContext(Dispatchers.IO) {
        val buildId = BuildId.newId()
        val testResultCollector = TestCollector(true, true)
        localSupervisorScope(exceptionHandler = {
            LOGGER.makeLoggingEventBuilder(Level.WARN)
                .addKeyValue("buildId", buildId)
                .log("Error during job launched during build", it)
        }) { scope ->
            val environment = connection.getModel(BuildEnvironment::class.java)

            //TODO is this necessary?
            setJavaHome(environment.java.javaHome)
            setJvmArguments(environment.java.jvmArguments)

            setEnvironmentVariables(System.getenv() + args.additionalEnvVars)
            @Suppress("UNCHECKED_CAST")
            withSystemProperties((System.getProperties().toMap() as Map<String, String>) + args.additionalSystemProps)
            addJvmArguments(args.additionalJvmArgs + "-Dscan.tag.MCP")
            withDetailedFailure()
            withArguments(args.allAdditionalArguments)
            setColorOutput(false)

            val cancelationTokenSource = GradleConnector.newCancellationTokenSource()
            withCancellationToken(cancelationTokenSource.token())

            currentCoroutineContext()[Job]?.invokeOnCompletion {
                if (it != null)
                    cancelationTokenSource.cancel()
            }

            additionalProgressListeners.forEach {
                addProgressListener(it.key, it.value)
            }

            addProgressListener(testResultCollector, testResultCollector.operations)


            val problems = ProblemsAccumulator()
            addProgressListener(object : ProgressListener {
                override fun statusChanged(event: ProgressEvent) {
                    if (event is ProblemAggregationEvent)
                        problems.add(event.problemAggregation)
                    if (event is SingleProblemEvent)
                        problems.add(event.problem)
                }
            }, OperationType.PROBLEMS)

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

            val scans = mutableListOf<GradleBuildScan>()

            val consoleOutput = StringBuilder()

            setStandardOutput(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = object : GradleStdoutWriter(config.allowPublicScansPublishing, {
                        consoleOutput.appendLine(it)
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
                            scans += GradleBuildScan.fromUrl(url)
                        }

                    }
                }.get()
            )

            setStandardError(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = LineEmittingWriter {
                        consoleOutput.appendLine("ERR: $it")
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
                if (it is BuildException || it is TestExecutionException) {
                    it
                } else {
                    throw it
                }
            }

            GradleResult.build(
                buildId,
                consoleOutput.toString(),
                scans,
                problems.aggregate(),
                testResultCollector.results(),
                exception,
                outcome
            )
        }.also {
            BuildResults.storeResult(it.buildResult)
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun <T : Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
        requiresGradleProject: Boolean = true
    ): GradleResult<T> {
        val connection = validateAndGetConnection(projectRoot, requiresGradleProject)
        val builder = connection.model(kClass.java)

        return builder.invokeBuild(
            connection,
            args,
            additionalProgressListeners,
            stdoutLineHandler,
            stderrLineHandler,
            tosAccepter,
            ModelBuilder<T>::get
        )
    }


    @OptIn(ExperimentalTime::class)
    suspend fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): GradleResult<Unit> {
        val connection = validateAndGetConnection(projectRoot)
        val builder = connection.newBuild()

        return builder.invokeBuild(
            connection,
            args,
            additionalProgressListeners,
            stdoutLineHandler,
            stderrLineHandler,
            tosAccepter,
            BuildLauncher::run
        )
    }

    /**
     * [testPatterns] is a map of task path -> test patterns
     */
    @OptIn(ExperimentalTime::class)
    suspend fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>> = emptyMap(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): GradleResult<Unit> {
        val connection = validateAndGetConnection(projectRoot)
        val builder = connection.newTestLauncher()

        builder.withTestsFor {
            testPatterns.forEach { (taskPath, patterns) ->
                it.forTaskPath(taskPath).includePatterns(patterns)
            }
        }

        return builder.invokeBuild(
            connection,
            args,
            additionalProgressListeners,
            stdoutLineHandler,
            stderrLineHandler,
            tosAccepter
        ) {
            it.run()
        }
    }
}