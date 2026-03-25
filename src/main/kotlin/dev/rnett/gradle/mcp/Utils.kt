package dev.rnett.gradle.mcp

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path


inline fun <R> runCatchingExceptCancellation(block: () -> R): Result<R> = runCatching {
    block()
}.apply {
    if (exceptionOrNull() is CancellationException)
        throw exceptionOrNull()!!
}

/**
 * A supervisor scope that is canceled once the block exits.
 */
suspend inline fun <R> localSupervisorScope(context: CoroutineContext = EmptyCoroutineContext, crossinline exceptionHandler: (Throwable) -> Unit = {}, block: (scope: CoroutineScope) -> R): R {
    val scope = CoroutineScope(context + SupervisorJob(currentCoroutineContext()[Job]) + CoroutineExceptionHandler { _, e -> exceptionHandler(e) })
    try {
        return block(scope)
    } finally {
        scope.cancel("Finished")
    }
}

inline fun <T, R> Collection<T>.mapToSet(block: (T) -> R): Set<R> = buildSet(this.size) {
    this@mapToSet.mapTo(this, block)
}

fun String.expandPath(): String {
    val path = if (this.startsWith("~")) {
        val home = System.getProperty("user.home")
        this.replaceFirst("~", home)
    } else {
        this
    }
    return Path(path).toAbsolutePath().normalize().toString()
}

fun ByteArray.hash(): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(this)
    return digest.fold("") { str, it -> str + "%02x".format(it) }.take(8)
}

data class GradleMcpEnvironment(val workingDir: Path) {
    val cacheDir: Path = workingDir.resolve("cache")

    init {
        cacheDir.toFile().mkdirs()
    }

    fun lockFile(root: String, path: String, kind: String, subDir: String): Path {
        val rootPath = root.replace(Regex("[^a-zA-Z0-9]"), "_")
        val safePath = path.replace(Regex("[^a-zA-Z0-9]"), "_")
        val filename = "${rootPath}_${safePath}_${kind}.lock"
        return cacheDir.resolve(".locks").resolve(subDir).resolve(filename)
    }

    fun lockFile(storagePath: Path, subDir: String): Path {
        val filename = "${storagePath.fileName}.lock"
        return cacheDir.resolve(".locks").resolve(subDir).resolve(filename)
    }

    fun dependencyLockFile(relativePrefix: String): Path {
        val safePath = relativePrefix.replace(Regex("[^a-zA-Z0-9]"), "_")
        return cacheDir.resolve(".locks").resolve("dependencies").resolve("$safePath.lock")
    }

    fun projectLockFile(storagePath: Path): Path {
        return lockFile(storagePath, "projects")
    }

    companion object {
        fun fromEnv(): GradleMcpEnvironment {
            val workingDir = System.getenv("GRADLE_MCP_WORKING_DIR") ?: "${System.getProperty("user.home")}/.gradle-mcp"
            return GradleMcpEnvironment(Path(workingDir).toAbsolutePath().normalize())
        }
    }
}