package dev.rnett.gradle.mcp.dependencies.search

import dev.rnett.gradle.mcp.ProgressReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

context(progress: ProgressReporter)
suspend fun indexSources(
    indexService: IndexService,
    indexBaseDir: Path,
    sourcesRoot: Path,
    providerToIndex: SearchProvider
) {
    coroutineScope {
        val channel = Channel<IndexEntry>(capacity = 20)

        val indexJob = async(Dispatchers.IO) {
            try {
                checkNotNull(indexService.indexFiles(indexBaseDir, channel.consumeAsFlow(), providerToIndex)) {
                    "Indexing returned no index for provider ${providerToIndex.name}"
                }
            } finally {
                for (entry in channel) {
                    // drain channel
                }
            }
        }

        try {
            Files.walk(sourcesRoot).use { stream ->
                for (file in stream) {
                    if (Files.isRegularFile(file)) {
                        val ext = file.fileName.toString().substringAfterLast('.', "")
                        if (ext in SearchProvider.SOURCE_EXTENSIONS) {
                            val relativePath = sourcesRoot.relativize(file).toString().replace('\\', '/')
                            channel.send(IndexEntry(relativePath) { file.readText() })
                        }
                    }
                }
            }
        } catch (e: Exception) {
            indexJob.cancel()
            throw e
        } finally {
            channel.close()
        }

        indexJob.await()
    }
}
