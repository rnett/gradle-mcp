package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.EnvSource
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.repl.ReplRequest
import dev.rnett.gradle.mcp.repl.ReplResponse
import dev.rnett.gradle.mcp.utils.EnvProvider
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.ExperimentalTime

class ReplTools(
    val gradle: GradleProvider,
    val replManager: ReplManager,
    val replEnvironmentService: ReplEnvironmentService,
    private val envProvider: EnvProvider
) : McpServerComponent("REPL Tools", "Tools for interacting with a Kotlin REPL session.") {
    companion object {
        val LOGGER = LoggerFactory.getLogger(ReplTools::class.java)!!
    }

    @Serializable
    enum class ReplCommand {
        @Description("Start (or replace) a session. Requires 'projectPath' and 'sourceSet'.")
        start,

        @Description("Terminate the session and release resources.")
        stop,

        @Description("Execute a Kotlin snippet in the active session. Requires 'code'.")
        run
    }

    @Serializable
    data class ReplArgs(
        val command: ReplCommand,
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Gradle project path (e.g., ':app'). Required for 'start'.")
        val projectPath: String? = null,
        @Description("Source set (e.g., 'main', 'test'). Required for 'start'. Must be JVM-compatible.")
        val sourceSet: String? = null,
        @Description("Additional classpath dependencies (e.g., 'group:artifact:version').")
        val additionalDependencies: List<String> = emptyList(),
        @Description("Environment variables for the REPL worker process.")
        val env: Map<String, String> = emptyMap(),
        @Description("Where to get the base environment variables from. Defaults to INHERIT.")
        val envSource: EnvSource = EnvSource.INHERIT,
        @Description("List of annotations to opt-in to (e.g., 'kotlinx.coroutines.ExperimentalCoroutinesApi').")
        val optIn: List<String> = emptyList(),
        @Description("Kotlin snippet to execute. Required for 'run'.")
        val code: String? = null
    )

    private var currentReplSessionId: String? = null

    @OptIn(ExperimentalTime::class)
    val repl by tool<ReplArgs, CallToolResult>(
        ToolNames.REPL,
        """
            |Provides a persistent, project-aware Kotlin REPL for prototyping, logic verification, and UI rendering within the project's JVM classpath.
            |
            |Prefer reading sources via `${ToolNames.SEARCH_DEPENDENCY_SOURCES}` / `${ToolNames.READ_DEPENDENCY_SOURCES}` for exploring APIs — the REPL is for dynamic behavior and prototyping.
            |After modifying project source code, `stop` then `start` to pick up classpath changes and the new compiled sources.
            |
            |### Commands
            |- **`start`**: Initialize a session. Requires `projectPath` and `sourceSet`.
            |- **`run`**: Execute a snippet. Session state (variables, imports) persists between calls.
            |- **`stop`**: Terminate the session and release JVM resources.
        """.trimMargin()
    ) {
        when (it.command) {
            ReplCommand.start -> startRepl(it, this)
            ReplCommand.stop -> stopRepl()
            ReplCommand.run -> runRepl(it)
        }
    }

    override suspend fun close() {
        replManager.closeAll()
    }

    private suspend fun startRepl(args: ReplArgs, context: McpToolContext): CallToolResult {
        val projectPath = args.projectPath
        val sourceSet = args.sourceSet
        if (projectPath == null || sourceSet == null) {
            return CallToolResult(listOf(TextContent("projectPath and sourceSet are required for 'start' command")), isError = true)
        }

        val projectRoot = context.run { args.projectRoot.resolve() }

        val envResult = try {
            with(context.progressReporter) {
                replEnvironmentService.resolveReplEnvironment(
                    projectRoot,
                    projectPath,
                    sourceSet,
                    args.additionalDependencies
                )
            }
        } catch (e: Exception) {
            return CallToolResult(listOf(TextContent(e.message ?: "Failed to resolve REPL environment")), isError = true)
        }

        currentReplSessionId?.let {
            replManager.terminateSession(it)
        }

        val sessionId = UUID.randomUUID().toString()

        val baseEnv = when (args.envSource) {
            EnvSource.NONE -> emptyMap()
            EnvSource.INHERIT -> envProvider.getInheritedEnvironment()
            EnvSource.SHELL -> envProvider.getShellEnvironment()
        }

        val mergedEnv = baseEnv + args.env
        val optInArgs = args.optIn.map { "-opt-in=$it" }

        val config = envResult.config.copy(
            env = mergedEnv,
            compilerArgs = envResult.config.compilerArgs + optInArgs
        )

        replManager.startSession(sessionId, config, envResult.javaExecutable)
        currentReplSessionId = sessionId
        return CallToolResult(listOf(TextContent("REPL session started with ID: $sessionId")))
    }

    private suspend fun stopRepl(): CallToolResult {
        return currentReplSessionId?.let {
            replManager.terminateSession(it)
            currentReplSessionId = null
            CallToolResult(listOf(TextContent("REPL session stopped.")))
        } ?: CallToolResult(listOf(TextContent("No active REPL session to stop.")))
    }

    private suspend fun runRepl(args: ReplArgs): CallToolResult {
        if (args.code == null) {
            return CallToolResult(listOf(TextContent("code is required for 'run' command")), isError = true)
        }
        val sessionId = currentReplSessionId ?: return CallToolResult(
            listOf(TextContent("No active REPL session. Start one with command 'start'.")),
            isError = true
        )

        val contents = mutableListOf<PromptMessageContent>()
        val textBuffer = StringBuilder()

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                contents.add(TextContent(textBuffer.toString()))
                textBuffer.clear()
            }
        }

        fun appendOutput(prefix: String, data: String) {
            data.lineSequence().forEach { line ->
                if (line.isNotEmpty()) {
                    textBuffer.append(prefix).append(": ").appendLine(line)
                } else {
                    textBuffer.appendLine()
                }
            }
        }

        fun handleData(data: ReplResponse.Data) {
            if (data.mime.startsWith("image/")) {
                flushText()
                contents.add(ImageContent(data.value, data.mime))
            } else {
                textBuffer.append(data.value)
            }
        }

        var isError = false
        replManager.sendRequest(sessionId, ReplRequest(args.code)).collect {
            when (it) {
                is ReplResponse.Output.Stdout -> appendOutput("STDOUT", it.data)
                is ReplResponse.Output.Stderr -> appendOutput("STDERR", it.data)
                is ReplResponse.Data -> handleData(it)
                is ReplResponse.Result.Success -> {
                    if (it.data.mime != "text/plain") {
                        handleData(it.data)
                    } else if (it.data.value.isNotBlank()) {
                        textBuffer.append(it.data.value)
                    }
                }

                is ReplResponse.Result.CompilationError -> {
                    isError = true
                    textBuffer.appendLine("\n\nCompilation Error:\n${it.message} at ${it.location ?: "unknown location"}")
                }

                is ReplResponse.Result.RuntimeError -> {
                    isError = true
                    textBuffer.appendLine("\n\nRuntime Error: ${it.message}${it.stackTrace?.let { "\n$it" } ?: ""}")
                }


                is ReplResponse.Result.InternalError -> {
                    isError = true
                    LOGGER.error("Internal error from REPL worker: {} with stack trace {}", it.message, it.stackTrace)
                    textBuffer.appendLine("\n\nInternal Error: ${it.message}")
                }

                is ReplResponse.Logging -> {
                    // handled in manager
                }
            }
        }
        flushText()
        return CallToolResult(contents, isError = isError)
    }
}
