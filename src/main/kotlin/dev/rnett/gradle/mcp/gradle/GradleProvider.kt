package dev.rnett.gradle.mcp.gradle

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.server.plugins.di.annotations.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.coroutines.resumeWithException
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration


class GradleProvider(@Property("gradle.tooling") val config: GradleConnectionConfiguration) {
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

    final suspend inline fun <reified T : Model> getBuildModel(
        projectRoot: String,
        jvmArguments: List<String>,
        systemProperties: Map<String, String>,
        environmentVariables: Map<String, String>,
        arguments: List<String>,
        additionalProgressListeners: List<ProgressListener> = emptyList(),
        noinline stdoutLineHandler: ((String) -> Unit)? = null,
        noinline stderrLineHandler: ((String) -> Unit)? = null,
    ): T {
        return getBuildModel(
            projectRoot,
            T::class,
            jvmArguments,
            systemProperties,
            environmentVariables,
            arguments,
            additionalProgressListeners,
            stdoutLineHandler,
            stderrLineHandler
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun <T : Model> getBuildModel(
        projectRoot: String,
        kClass: KClass<T>,
        jvmArguments: List<String>,
        systemProperties: Map<String, String>,
        environmentVariables: Map<String, String>,
        arguments: List<String>,
        additionalProgressListeners: List<ProgressListener> = emptyList(),
        stdoutLineHandler: ((String) -> Unit)? = null,
        stderrLineHandler: ((String) -> Unit)? = null,
    ): T = withContext(Dispatchers.IO) {
        val connection = validateAndGetConnection(projectRoot)
        val builder = connection.model(kClass.java)

        val environment = connection.getModel(BuildEnvironment::class.java)

        //TODO is this necessary?
        builder.setJavaHome(environment.java.javaHome)
        builder.setJvmArguments(environment.java.jvmArguments)

        builder.setEnvironmentVariables(System.getenv() + environmentVariables)
        builder.withSystemProperties((System.getProperties().toMap() as Map<String, String>) + systemProperties)
        builder.addJvmArguments(jvmArguments)
        builder.withDetailedFailure()
        builder.withArguments(arguments)
        builder.setColorOutput(false)

        val cancelationTokenSource = GradleConnector.newCancellationTokenSource()
        builder.withCancellationToken(cancelationTokenSource.token())

        currentCoroutineContext()[Job]?.invokeOnCompletion {
            if (it != null)
                cancelationTokenSource.cancel()
        }

        additionalProgressListeners.forEach {
            builder.addProgressListener(it)
        }

        builder.setStandardOutput(
            WriterOutputStream.builder().apply {
                charset = StandardCharsets.UTF_8
                bufferSize = 80
                writer = LineEmittingWriter {
                    stdoutLineHandler?.invoke(it)
                    LOGGER.info("Build stdout: $it")
                }
            }.get()
        )

        builder.setStandardError(
            WriterOutputStream.builder().apply {
                charset = StandardCharsets.UTF_8
                bufferSize = 80
                writer = LineEmittingWriter {
                    stderrLineHandler?.invoke(it)
                    LOGGER.info("Build err: $it")
                }
            }.get()
        )

        suspendCancellableCoroutine { coroutine ->
            builder.get(object : ResultHandler<T> {
                override fun onComplete(result: T) {
                    coroutine.resume(result) { cause, _, _ -> /* no-op */ }
                }

                override fun onFailure(failure: GradleConnectionException) {
                    coroutine.resumeWithException(failure)
                }
            })
        }
    }

}