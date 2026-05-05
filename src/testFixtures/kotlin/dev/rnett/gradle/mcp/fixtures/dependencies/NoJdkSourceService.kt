package dev.rnett.gradle.mcp.fixtures.dependencies

import dev.rnett.gradle.mcp.ProgressReporter
import dev.rnett.gradle.mcp.dependencies.DefaultSourcesService
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.JdkSourceService
import dev.rnett.gradle.mcp.dependencies.SourceStorageService
import dev.rnett.gradle.mcp.dependencies.model.CASDependencySourcesDir
import dev.rnett.gradle.mcp.dependencies.search.IndexService
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object NoJdkSourceService : JdkSourceService {
    context(progress: ProgressReporter)
    override suspend fun resolveSources(
        jdkHome: String,
        forceDownload: Boolean,
        fresh: Boolean,
        providerToIndex: SearchProvider?
    ): CASDependencySourcesDir? = null
}

fun createTestSourcesService(
    depService: GradleDependencyService,
    storageService: SourceStorageService,
    indexService: IndexService,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): DefaultSourcesService = DefaultSourcesService(
    depService,
    storageService,
    indexService,
    NoJdkSourceService,
    dispatcher
)