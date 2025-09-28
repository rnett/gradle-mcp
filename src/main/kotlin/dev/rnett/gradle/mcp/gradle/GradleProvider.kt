package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import dev.rnett.gradle.mcp.localSupervisorScope
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import dev.rnett.gradle.mcp.tools.GradleInvocationArguments
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
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

class GradleProvider(val config: GradleConfiguration) {
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

    suspend fun validateAndGetConnection(projectRoot: String): ProjectConnection {
        val path = Path(projectRoot)
        if (path.notExists()) {
            throw IllegalStateException("Provided project root path \"$path\" does not exist")
        }
        if (!path.isDirectory()) {
            throw IllegalStateException("Provided project root path \"$path\" is not a directory")
        }

        return getConnection(path)
    }

    private suspend inline fun <I : ConfigurableLauncher<*>, R> I.invokeBuild(
        connection: ProjectConnection,
        args: GradleInvocationArguments,
        additionalProgressListeners: List<ProgressListener>,
        noinline stdoutLineHandler: ((String) -> Unit)?,
        noinline stderrLineHandler: ((String) -> Unit)?,
        crossinline tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        crossinline invoker: (I) -> R,
    ): GradleResult<R> = withContext(Dispatchers.IO) {
        val buildId = UUID.randomUUID().toString()
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
            withSystemProperties((System.getProperties().toMap() as Map<String, String>) + args.additionalSystemProps)
            addJvmArguments(args.additionalJvmArgs)
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
                addProgressListener(it)
            }

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

            setStandardOutput(
                WriterOutputStream.builder().apply {
                    charset = StandardCharsets.UTF_8
                    bufferSize = 80
                    writer = object : GradleStdoutWriter(config.allowPublicScansPublishing, {
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
                        stderrLineHandler?.invoke(it)
                        LOGGER.makeLoggingEventBuilder(Level.INFO)
                            .addKeyValue("buildId", buildId)
                            .log("Build stderr: $it")
                    }
                }.get()
            )

            setStandardInput(inputStream)

            runCatchingExceptCancellation {
                scope.async {
                    invoker(this@invokeBuild)
                }.await()
            }
                .fold(
                    { GradleResult.Success(it, scans) },
                    {
                        if (it is BuildException || it is TestExecutionException) {
                            GradleResult.Failure(it, scans)
                        } else {
                            throw it
                        }
                    }
                )
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun <T : Model> getBuildModel(
        projectRoot: String,
        kClass: KClass<T>,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: List<ProgressListener> = emptyList(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): GradleResult<T> = withContext(Dispatchers.IO) {
        val connection = validateAndGetConnection(projectRoot)
        val builder = connection.model(kClass.java)

        return@withContext builder.invokeBuild(
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
        projectRoot: String,
        args: GradleInvocationArguments,
        tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
        additionalProgressListeners: List<ProgressListener> = emptyList(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): GradleResult<Unit> = withContext(Dispatchers.IO) {
        val connection = validateAndGetConnection(projectRoot)
        val builder = connection.newBuild()

        return@withContext builder.invokeBuild(
            connection,
            args,
            additionalProgressListeners,
            stdoutLineHandler,
            stderrLineHandler,
            tosAccepter,
            BuildLauncher::run
        )
    }
}