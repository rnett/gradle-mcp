package dev.rnett.gradle.mcp.lucene

import com.github.benmanes.caffeine.cache.Caffeine
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class LuceneReaderCache(
    maximumSize: Long = 10,
    expireAfterAccessMinutes: Long = 30
) : AutoCloseable {

    private val cache = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .expireAfterAccess(expireAfterAccessMinutes, TimeUnit.MINUTES)
        .removalListener<Path, DirectoryReader> { _, reader, _ ->
            reader?.close()
        }
        .build<Path, DirectoryReader> { path ->
            DirectoryReader.open(FSDirectory.open(path))
        }

    fun get(path: Path): DirectoryReader {
        return try {
            cache.get(path)
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is org.apache.lucene.index.IndexNotFoundException) {
                throw IllegalStateException("Lucene index not found in $path", cause)
            }
            throw e
        }
    }

    fun invalidate(path: Path) {
        cache.invalidate(path)
    }

    override fun close() {
        cache.invalidateAll()
        cache.cleanUp()
    }
}
