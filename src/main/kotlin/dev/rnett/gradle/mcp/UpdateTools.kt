package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.gradle.BackgroundBuildManager
import dev.rnett.gradle.mcp.gradle.BuildResults
import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.GradleScanTosAcceptRequest
import dev.rnett.gradle.mcp.gradle.RunningBuild
import dev.rnett.gradle.mcp.mcp.McpServerComponent
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

    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val directory = args.getOrNull(0)?.let { Path(it) }
        val verify = args.contains("--verify")

        if (directory != null && directory.exists() && !directory.isDirectory()) {
            throw IllegalArgumentException("Output directory must be a directory")
        }

        val files = DI.components(throwingGradleProvider, throwingReplManager).mapNotNull {
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

    val throwingGradleProvider = object : GradleProvider {
        override fun <T : org.gradle.tooling.model.Model> getBuildModel(
            projectRoot: GradleProjectRoot,
            kClass: kotlin.reflect.KClass<T>,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
            requiresGradleProject: Boolean
        ): RunningBuild<T> {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override fun runBuild(
            projectRoot: GradleProjectRoot,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
        ): RunningBuild<Unit> {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override fun runTests(
            projectRoot: GradleProjectRoot,
            testPatterns: Map<String, Set<String>>,
            args: GradleInvocationArguments,
            tosAccepter: suspend (GradleScanTosAcceptRequest) -> Boolean,
            additionalProgressListeners: Map<ProgressListener, Set<OperationType>>,
            stdoutLineHandler: ((String) -> Unit)?,
            stderrLineHandler: ((String) -> Unit)?,
        ): RunningBuild<Unit> {
            throw UnsupportedOperationException("Not used for tool listing")
        }

        override fun close() {
        }

        override val backgroundBuildManager = BackgroundBuildManager()
        override val buildResults = BuildResults(backgroundBuildManager)
    }

    val throwingReplManager = object : dev.rnett.gradle.mcp.repl.ReplManager {
        override fun startSession(
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
}