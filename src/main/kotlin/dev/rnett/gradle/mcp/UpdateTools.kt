package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.BuildManager
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.RunningBuild
import dev.rnett.gradle.mcp.maven.MavenCentralSearchResponse
import dev.rnett.gradle.mcp.maven.MavenCentralService
import dev.rnett.gradle.mcp.maven.MavenRepoService
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.ReplConfigWithJava
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.tools.PaginationInput
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

    private val throwingGradleSourceService = object : dev.rnett.gradle.mcp.dependencies.GradleSourceService {
        context(progress: ProgressReporter)
        override suspend fun getGradleSources(projectRoot: GradleProjectRoot, forceDownload: Boolean): dev.rnett.gradle.mcp.dependencies.SourcesDir =
            throw UnsupportedOperationException("Not supported in tool generator")
    }

    private val throwingGradleVersionService = object : GradleVersionService {
        override suspend fun resolveVersion(version: String?): String =
            throw UnsupportedOperationException("Not supported in tool generator")
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
            throwingGradleProvider,
            throwingReplManager,
            throwingReplEnvironmentService,
            throwingGradleDocsService,
            throwingGradleVersionService,
            throwingGradleDependencyService,
            throwingMavenRepoService,
            throwingMavenCentralService,
            throwingSourcesService,
            throwingGradleSourceService
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

    val throwingSourcesService = object : dev.rnett.gradle.mcp.dependencies.SourcesService {
        context(progress: ProgressReporter)
        override suspend fun downloadAllSources(projectRoot: GradleProjectRoot, index: Boolean, forceDownload: Boolean, fresh: Boolean): dev.rnett.gradle.mcp.dependencies.SourcesDir {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        context(progress: ProgressReporter)
        override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): dev.rnett.gradle.mcp.dependencies.SourcesDir {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        context(progress: ProgressReporter)
        override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): dev.rnett.gradle.mcp.dependencies.SourcesDir {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        context(progress: ProgressReporter)
        override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String, index: Boolean, forceDownload: Boolean, fresh: Boolean): dev.rnett.gradle.mcp.dependencies.SourcesDir {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun search(sources: dev.rnett.gradle.mcp.dependencies.SourcesDir, provider: dev.rnett.gradle.mcp.dependencies.search.SearchProvider, query: String, pagination: PaginationInput): dev.rnett.gradle.mcp.dependencies.search.SearchResponse<dev.rnett.gradle.mcp.dependencies.search.SearchResult> {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun listPackageContents(sources: dev.rnett.gradle.mcp.dependencies.SourcesDir, packageName: String): dev.rnett.gradle.mcp.dependencies.search.PackageContents? {
            throw UnsupportedOperationException("Not used for tool listing")
        }
    }

    val throwingGradleProvider = object : GradleProvider {
        override suspend fun <T : org.gradle.tooling.model.Model> getBuildModel(
            projectRoot: GradleProjectRoot,
            kClass: kotlin.reflect.KClass<T>,
            args: GradleInvocationArguments,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
            progress: ProgressReporter,
            requiresGradleProject: Boolean
        ): dev.rnett.gradle.mcp.gradle.GradleResult<T> {
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

    val throwingReplEnvironmentService = object : ReplEnvironmentService {
        override suspend fun resolveReplEnvironment(
            projectRoot: GradleProjectRoot,
            projectPath: String,
            sourceSet: String,
            additionalDependencies: List<String>
        ): ReplConfigWithJava {
            throw UnsupportedOperationException("Not supported in UpdateTools")
        }
    }

    val throwingReplManager = object : dev.rnett.gradle.mcp.repl.ReplManager {
        override suspend fun startSession(
            sessionId: String,
            config: dev.rnett.gradle.mcp.repl.ReplConfig,
            javaExecutable: String
        ): Process {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override fun getSession(sessionId: String): dev.rnett.gradle.mcp.repl.ReplSession? {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun terminateSession(sessionId: String) {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun closeAll() {
        }

        override suspend fun sendRequest(
            sessionId: String,
            command: dev.rnett.gradle.mcp.repl.ReplRequest
        ): Flow<dev.rnett.gradle.mcp.repl.ReplResponse> {
            throw UnsupportedOperationException("Not used for tool listing")
        }
    }

    val throwingGradleDocsService = object : dev.rnett.gradle.mcp.dependencies.gradle.docs.GradleDocsService {
        context(progress: ProgressReporter)
        override suspend fun getDocsPageContent(path: String, version: String?): dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsPageContent {
            throw UnsupportedOperationException("Not used for tool listing")
        }
        context(progress: ProgressReporter)
        override suspend fun getReleaseNotes(version: String?): String {
            throw UnsupportedOperationException("Not used for tool listing")
        }
        context(progress: ProgressReporter)
        override suspend fun searchDocs(query: String, version: String?): dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsSearchResponse {
            throw UnsupportedOperationException("Not used for tool listing")
        }
        context(progress: ProgressReporter)
        override suspend fun summarizeSections(version: String?): List<dev.rnett.gradle.mcp.dependencies.gradle.docs.DocsSectionSummary> {
            throw UnsupportedOperationException("Not used for tool listing")
        }
        override fun close() {
        }
    }
    
    val throwingGradleDependencyService = object : dev.rnett.gradle.mcp.dependencies.GradleDependencyService {
        override suspend fun getDependencies(
            projectRoot: GradleProjectRoot,
            projectPath: String?,
            configuration: String?,
            sourceSet: String?,
            checkUpdates: Boolean,
            versionFilter: String?,
            onlyDirect: Boolean,
            downloadSources: Boolean
        ): dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun getSourceSetDependencies(
            projectRoot: GradleProjectRoot,
            sourceSetPath: String
        ): dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencyReport {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun getConfigurationDependencies(
            projectRoot: GradleProjectRoot,
            configurationPath: String): dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun downloadAllSources(projectRoot: GradleProjectRoot): dev.rnett.gradle.mcp.dependencies.model.GradleDependencyReport {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun downloadProjectSources(projectRoot: GradleProjectRoot, projectPath: String): dev.rnett.gradle.mcp.dependencies.model.GradleProjectDependencies {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun downloadConfigurationSources(projectRoot: GradleProjectRoot, configurationPath: String): dev.rnett.gradle.mcp.dependencies.model.GradleConfigurationDependencies {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override suspend fun downloadSourceSetSources(projectRoot: GradleProjectRoot, sourceSetPath: String): dev.rnett.gradle.mcp.dependencies.model.GradleSourceSetDependencyReport {
            throw UnsupportedOperationException("Not used for tool listing")
        }
    }

    val throwingMavenRepoService = object : MavenRepoService {
        override suspend fun getVersions(repository: String, group: String, artifact: String): List<String> {
            throw UnsupportedOperationException("Not used for tool listing")
        }
    }

    val throwingMavenCentralService = object : MavenCentralService {
        override suspend fun searchCentral(
            query: String,
            start: Int,
            results: Int
        ): MavenCentralSearchResponse.Response {
            throw UnsupportedOperationException("Not used for tool listing")
        }
    }
}