package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.ReplEnvironmentService
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.repl.ReplRequest
import dev.rnett.gradle.mcp.repl.ReplResponse
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.ExperimentalTime

class ReplTools(
    val gradle: GradleProvider,
    val replManager: ReplManager,
    val replEnvironmentService: ReplEnvironmentService
) : McpServerComponent("REPL Tools", "Tools for interacting with a Kotlin REPL session.") {
    companion object {
        val LOGGER = LoggerFactory.getLogger(ReplTools::class.java)!!
    }

    @Serializable
    enum class ReplCommand {
        @Description("Starts (or replaces) a persistent REPL session. Requires 'projectPath' and 'sourceSet'.")
        start,

        @Description("Terminates the current REPL session and releases resources.")
        stop,

        @Description("Executes a Kotlin code snippet within the active REPL session. Requires 'code'.")
        run
    }

    @Serializable
    data class ReplArgs(
        @Description("Executing an authoritative command: 'start', 'stop', or 'run'.")
        val command: ReplCommand,
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("Specifying the Gradle project path (e.g., ':app', ':library'). Required for 'start'.")
        val projectPath: String? = null,
        @Description("Specifying the source set (e.g., 'main', 'test'). Required for 'start'. Must be JVM-compatible.")
        val sourceSet: String? = null,
        @Description("Adding additional dependencies to the REPL classpath via authoritative notation (e.g., 'group:artifact:version').")
        val additionalDependencies: List<String> = emptyList(),
        @Description("Setting environment variables in the REPL worker process.")
        val env: Map<String, String> = emptyMap(),
        @Description("Executing a Kotlin code snippet within the active session. Required for 'run'.")
        val code: String? = null
    )

    private var currentReplSessionId: String? = null

    @OptIn(ExperimentalTime::class)
    val repl by tool<ReplArgs, CallToolResult>(
        ToolNames.REPL,
        """
            |ALWAYS use this tool to interactively prototype Kotlin code, explore APIs, or verify UI components directly within the project's runtime context.
            |It provides a persistent session with full access to project classes and dependencies, saving you the overhead of writing and running temporary test files.
            |
            |### Prototyping & Exploration Best Practices
            |
            |1.  **Prefer Reading Sources**: For exploring unfamiliar library APIs or internal project utilities, ALWAYS prefer reading the source code first using `${ToolNames.SEARCH_DEPENDENCY_SOURCES}` and `${ToolNames.READ_DEPENDENCY_SOURCES}`. Reading the source provides complete context, implementation details, and documentation that a REPL cannot easily expose.
            |2.  **Use REPL for Prototyping**: Only use the REPL when you need to verify dynamic behavior, test small snippets of logic, or prototype a new feature before implementing it.
            |3.  **Iterative Development**: Use `run` for rapid experimentation. If you modify project source code, you MUST `stop` and `start` the REPL again to pick up the new classes.
            |4.  **UI Verification**: The REPL can render and return UI components (e.g., Compose previews) as images.
            |
            |### Authoritative Commands
            |
            |1.  **`start`**: Initializes (or replaces) a persistent REPL session. Requires `projectPath` and `sourceSet`.
            |2.  **`run`**: Executes a Kotlin code snippet within the active session. The session state (variables, imports) persists between calls.
            |3.  **`stop`**: Terminates the session and releases JVM resources.
            |
            |### Advanced Configuration
            |
            |1.  **Dependencies**: Use `additionalDependencies` (group:artifact:version) to pull in external libraries not already in the project.
            |2.  **Environment**: Use `env` to set specific system properties or environment variables for the REPL worker.
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

        val sessionId = UUID.randomUUID().toString()
        val config = envResult.config.copy(env = args.env)

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
