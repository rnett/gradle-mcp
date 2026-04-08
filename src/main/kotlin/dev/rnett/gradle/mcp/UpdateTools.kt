package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.dependencies.DependencyRequestOptions
import dev.rnett.gradle.mcp.dependencies.GradleDependencyService
import dev.rnett.gradle.mcp.dependencies.GradleSourceService
import dev.rnett.gradle.mcp.dependencies.SourcesService
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsPageContent
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsSearchResponse
import dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsSectionSummary
import dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService
import dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies
import dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencyReport
import dev.rnett.gradle.mcp.dependencies.model.SourcesDir
import dev.rnett.gradle.mcp.dependencies.search.PackageContents
import dev.rnett.gradle.mcp.dependencies.search.SearchProvider
import dev.rnett.gradle.mcp.dependencies.search.SearchResponse
import dev.rnett.gradle.mcp.dependencies.search.SearchResult
import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleResult
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.maven.DepsDevService
import dev.rnett.gradle.mcp.maven.DepsDevVersion
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.ReplConfig
import dev.rnett.gradle.mcp.repl.ReplConfigWithJava
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplRequest
import dev.rnett.gradle.mcp.repl.ReplResponse
import dev.rnett.gradle.mcp.repl.ReplSession
import dev.rnett.gradle.mcp.tools.PaginationInput
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass

object UpdateTools {
    private val START = "[//]: # (<<TOOLS_LIST_START>>)\n"
    private val END = "[//]: # (<<TOOLS_LIST_END>>)\n"

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun StringBuilder.appendDetails(summary: String, block: StringBuilder.() -> Unit) {
        appendLine()
        appendLine("<details>")
        appendLine()
        appendLine("<summary>$summary</summary>")
        appendLine()
        block()
        appendLine()
        appendLine("</details>")
    }

    private inline fun <reified T> StringBuilder.appendJson(value: T) {
        appendLine()
        appendLine("```json")
        appendLine(json.encodeToString(value))
        appendLine("```")
        appendLine()
    }

    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val directory = args.getOrNull(0)?.let { Path(it) }
        val verify = args.contains("--verify")

        if (directory != null && directory.exists() && !directory.isDirectory()) {
            throw IllegalArgumentException("Output directory must be a directory")
        }

        val files = DI.components(
            ThrowingGradleProvider,
            ThrowingReplManager,
            ThrowingReplEnvironmentService,
            ThrowingEnvProvider,
            ThrowingGradleDocsService,
            ThrowingGradleVersionService,
            ThrowingGradleDependencyService,
            ThrowingDepsDevService,
            ThrowingSourcesService,
            ThrowingGradleSourceService,
            ThrowingSourceIndexService
        ).mapNotNull {
            val file = directory?.resolve("${it.name.replace(" ", "_").uppercase()}.md")
            executeForComponent(it, file, verify)
            file
        }.toSet()

        if (directory != null && verify) {
            val extra = directory.listDirectoryEntries().toSet() - files
            if (extra.isNotEmpty()) {
                throw IllegalArgumentException("Unexpected files in output directory: $extra")
            }
        }

        val mkdocsFile = Path("mkdocs.yml")
        if (mkdocsFile.exists()) {
            val content = mkdocsFile.readText()
            val navLine = content.indexOf("nav:")
            if (navLine != -1) {
                val navContent = content.substring(navLine)
                val regex = Regex("""(?m)^ *-.*: (.*\.md)$|(?m)^ *- (.*\.md)$""")
                val referencedFiles = regex.findAll(navContent).map {
                    it.groups[1]?.value ?: it.groups[2]!!.value
                }.toSet()

                val docsDir = Path("docs")
                val missingFiles = referencedFiles.filter { !docsDir.resolve(it).exists() }
                if (missingFiles.isNotEmpty()) {
                    throw IllegalArgumentException("Files referenced in mkdocs.yml do not exist: $missingFiles")
                }
            }
        }
    }

    fun executeForComponent(component: McpServerComponent, path: Path?, isVerify: Boolean) {
        val server = DI.createServer(DI.json, listOf(component))

        val text = buildString {
            appendLine("[//]: # (@formatter:off)")
            appendLine()
            appendLine("# ${component.name}")
            appendLine()

            appendLine(component.description)

            appendLine()

            server.tools.forEach {
                appendLine("## ${it.key}")
                if (it.value.tool.title != null) {
                    appendLine(it.value.tool.title)
                    appendLine()
                }
                appendLine()
                appendLine(it.value.tool.description)
                appendDetails("Input schema") {
                    appendJson(it.value.tool.inputSchema)
                }
                appendLine()
                if (it.value.tool.outputSchema != null) {
                    appendDetails("Output schema") {
                        appendJson(it.value.tool.outputSchema)
                    }
                }
                appendLine()
            }
            appendLine()
            appendLine()
        }

        if (path != null) {
            if (path.exists()) {
                if (!path.isRegularFile())
                    throw IllegalArgumentException("Output path $path is a directory, not a file.")

                val existing = path.readText()

                val newText = if (START in existing || END in existing) {
                    val before = existing.substringBefore(START)
                    val after = existing.substringAfter(END, "")
                    "$before$START\n$text\n$END$after"
                } else {
                    text
                }
                if (isVerify) {
                    if (newText != existing) {
                        throw IllegalStateException("Existing tools description did not match, update tools description")
                    }
                } else {
                    path.writeText(newText)
                }
            } else {
                path.createParentDirectories()
                path.writeText(text)
            }
        } else {
            println(text)
        }
    }
}

private object ThrowingEnvProvider : EnvProvider {
    override fun getShellEnvironment(): Map<String, String> = throw UnsupportedOperationException("Not supported in tool generator")
    override fun getInheritedEnvironment(): Map<String, String> = throw UnsupportedOperationException("Not supported in tool generator")
}

private object ThrowingSourcesService : SourcesService {
    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessProjectSources(projectRoot: GradleProjectRoot, projectPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun resolveAndProcessSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, dependency: String?, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir {
        throw UnsupportedOperationException("Not used for tool listing")
    }
}

private object ThrowingSourceIndexService : dev.rnett.gradle.mcp.dependencies.SourceIndexService {
    override suspend fun search(sources: SourcesDir, provider: SearchProvider, query: String, pagination: PaginationInput): SearchResponse<SearchResult> {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override suspend fun listPackageContents(sources: SourcesDir, packageName: String): PackageContents? {
        throw UnsupportedOperationException("Not used for tool listing")
    }
}

private object ThrowingGradleProvider : GradleProvider {
    override suspend fun <T : org.gradle.tooling.model.Model> getBuildModel(
        projectRoot: GradleProjectRoot,
        kClass: KClass<T>,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter,
        requiresGradleProject: Boolean
    ): GradleResult<T> {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override fun runBuild(
        projectRoot: GradleProjectRoot,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter
    ): RunningBuild {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override fun runTests(
        projectRoot: GradleProjectRoot,
        testPatterns: Map<String, Set<String>>,
        args: GradleInvocationArguments,
        additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
        stdoutLineHandler: ((String) -> Unit)?,
        stderrLineHandler: ((String) -> Unit)?,
        progress: ProgressReporter
    ): RunningBuild {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override fun close() {
    }

    override val buildManager = BuildManager()
}

private object ThrowingReplEnvironmentService : ReplEnvironmentService {
    context(progress: ProgressReporter)
    override suspend fun resolveReplEnvironment(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        sourceSet: String,
        additionalDependencies: List<String>
    ): ReplConfigWithJava {
        throw UnsupportedOperationException("Not supported in UpdateTools")
    }
}

private object ThrowingReplManager : dev.rnett.gradle.mcp.repl.ReplManager {
    override suspend fun startSession(
        sessionId: String,
        config: ReplConfig,
        javaExecutable: String
    ): Process {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override fun getSession(sessionId: String): ReplSession? {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override suspend fun terminateSession(sessionId: String) {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override suspend fun closeAll() {
    }

    override suspend fun sendRequest(
        sessionId: String,
        command: ReplRequest
    ): Flow<ReplResponse> {
        throw UnsupportedOperationException("Not used for tool listing")
    }
}

private object ThrowingGradleDocsService : GradleDocsService {
    context(progress: ProgressReporter)
    override suspend fun getDocsPageContent(path: String, version: String?): DocsPageContent {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun getReleaseNotes(version: String?): String {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun searchDocs(query: String, version: String?): DocsSearchResponse {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun summarizeSections(version: String?): List<DocsSectionSummary> {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    override fun close() {
    }
}

private object ThrowingGradleDependencyService : GradleDependencyService {
    context(progress: ProgressReporter)
    override suspend fun getDependencies(
        projectRoot: GradleProjectRoot,
        projectPath: String?,
        options: DependencyRequestOptions
    ): GradleDependencyReport {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun getSourceSetDependencies(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String?,
        fresh: Boolean
    ): GradleSourceSetDependencyReport {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun getConfigurationDependencies(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String?,
        fresh: Boolean
    ): GradleConfigurationDependencies {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, dependency: String?, fresh: Boolean): GradleDependencyReport {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadProjectSources(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        dependency: String?,
        fresh: Boolean,
        includeInternal: Boolean
    ): GradleProjectDependencies {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadConfigurationSources(
        projectRoot: GradleProjectRoot,
        configurationPath: String,
        dependency: String?,
        fresh: Boolean,
        includeInternal: Boolean
    ): GradleConfigurationDependencies {
        throw UnsupportedOperationException("Not used for tool listing")
    }

    context(progress: ProgressReporter)
    override suspend fun downloadSourceSetSources(
        projectRoot: GradleProjectRoot,
        sourceSetPath: String,
        dependency: String?,
        fresh: Boolean,
        includeInternal: Boolean
    ): GradleSourceSetDependencyReport {
        throw UnsupportedOperationException("Not used for tool listing")
    }
}

private object ThrowingDepsDevService : DepsDevService {
    override suspend fun getMavenVersions(group: String, artifact: String): List<DepsDevVersion> {
        throw UnsupportedOperationException("Not used for tool listing")
    }
}

private object ThrowingGradleSourceService : GradleSourceService {
    context(progress: ProgressReporter)
    override suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean, fresh: Boolean, providerToIndex: SearchProvider?): SourcesDir =
        throw UnsupportedOperationException("Not supported in tool generator")
}

private object ThrowingGradleVersionService : GradleVersionService {
    override suspend fun resolveVersion(version: String?): String =
        throw UnsupportedOperationException("Not supported in tool generator")
}
