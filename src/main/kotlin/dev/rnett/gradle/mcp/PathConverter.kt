package dev.rnett.gradle.mcp

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.notExists

interface PathConverter {
    fun getExistingDirectory(path: String) = getExistingPath(path).also {
        if (!it.isDirectory()) throw IllegalArgumentException("Path $it is not a directory")
    }

    fun convertPath(path: String): Path

    fun getExistingPath(path: String): Path {
        return try {
            convertPath(path).toRealPath().also {
                if (it.notExists()) {
                    throw NoSuchFileException(it.toFile())
                }
            }
        } catch (convertedException: java.nio.file.NoSuchFileException) {
            try {
                Path(path).toRealPath()
            } catch (e: java.nio.file.NoSuchFileException) {
                throw IllegalArgumentException("Path $path does not exist and could not be converted", convertedException).apply {
                    addSuppressed(e)
                }
            }
        }
    }

    object NoOp : PathConverter {
        override fun convertPath(path: String): Path {
            return Path(path)
        }
    }
}

fun Path.resolve(others: Iterable<String>): Path {
    var p = this
    others.forEach { p = p.resolve(it) }
    return p
}

class NewRootPathConverter(val root: Path) : PathConverter {
    private val LOGGER = LoggerFactory.getLogger(NewRootPathConverter::class.java)

    override fun convertPath(path: String): Path {
        runCatchingExceptCancellation { Path(path).toRealPath() }.getOrNull()?.takeIf { it.exists() }?.let { return it }

        val p = Path(path)
        if (path.substring(1).startsWith(":\\")) {
            // it's a windows path
            val drive = path.substringBefore(":\\").lowercase()
            val parts = path.substringAfter(":\\").split('\\')
            // ends up looking like /<root>/C/my/path
            return root.resolve(drive).resolve(parts)
        } else if (path.startsWith("/")) {
            return root.resolve(p.map { it.name })
        } else {
            return p
        }
    }
}