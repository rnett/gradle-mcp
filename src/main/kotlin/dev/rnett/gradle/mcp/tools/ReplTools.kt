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
        @Description("Starts (or replaces) a persistent REPL session. Requires 'projectPath' and 'sourceSet' to establish the authoritative environment.")
        start,

        @Description("Authoritatively terminates the current REPL session and releases all associated resources.")
        stop,

        @Description("Executes a Kotlin code snippet within the active, high-context REPL session. Requires 'code'.")
        run
    }

    @Serializable
    data class ReplArgs(
        @Description("The authoritative command to execute: 'start', 'stop', or 'run'.")
        val command: ReplCommand,
        @Description("The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project context.")
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path (e.g., ':app', ':library'). Required for the 'start' command to establish the classpath.")
        val projectPath: String? = null,
        @Description("The source set to use (e.g., 'main', 'test'). Required for the 'start' command. Must be a JVM-compatible source set.")
        val sourceSet: String? = null,
        @Description("Additional dependencies to add to the REPL classpath, using authoritative Gradle dependency notation (e.g., 'group:artifact:version'). Optional for 'start'.")
        val additionalDependencies: List<String> = emptyList(),
        @Description("A map of environment variables to set in the REPL worker process. Optional for 'start'.")
        val env: Map<String, String> = emptyMap(),
        @Description("The Kotlin code snippet to execute within the active session. Required for the 'run' command.")
        val code: String? = null
    )

    private var currentReplSessionId: String? = null

    @OptIn(ExperimentalTime::class)
    val repl by tool<ReplArgs, CallToolResult>(
        ToolNames.REPL,
        """
            |The authoritative tool for executing Kotlin code interactively within your project's full runtime context.
            |It provides a managed execution environment with direct access to your project's source sets, dependencies, and compiler configuration.
            |
            |### Authoritative Features
            |- **Deep Context Integration**: Unlike standalone REPLs, this tool uses your project's exact classpath. Call your project's functions, instantiate its classes, and use its libraries with absolute precision.
            |- **Persistent Execution State**: Maintain variables, functions, and imports across multiple `run` calls within a single session.
            |- **Rich Output Rendering**: Authoritatively render images (Compose/AWT), Markdown, and HTML directly to your context via the `responder` API.
            |- **Managed Lifecycle**: Explicit `start` and `stop` commands ensure resources are managed efficiently.
            |
            |### Common Usage Patterns
            |- **Prototyping Logic**: Rapidly test a complex algorithm or project utility without writing a full test suite.
            |- **API Exploration**: Interactively explore a new library's API within your project's environment.
            |- **UI Verification**: Render Compose components to images for instant visual feedback (see the `compose-view` skill).
            |- **Debugging**: Authoritatively inspect the state of your project or its dependencies at runtime.
            |
            |### Execution and Result Handling
            |- **Standard Streams**: Both `stdout` and `stderr` are captured and returned as authoritative text.
            |- **Automatic Rendering**: The result of the last expression in a `run` call is automatically rendered.
            |- **The Responder API**: Use the `responder: dev.rnett.gradle.mcp.repl.Responder` property for manual, rich output (e.g., `responder.render(myBitmap)`).
            |
            |**Safety Note**: Changes to project source code are NOT reflected in an active session. You MUST `stop` and `start` the REPL to pick up changes to project classes.
            |For detailed interactive workflows, refer to the `gradle-repl` skill.
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
            replEnvironmentService.resolveReplEnvironment(
                projectRoot,
                projectPath,
                sourceSet,
                args.additionalDependencies
            )
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
