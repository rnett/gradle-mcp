package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.ReplConfig
import dev.rnett.gradle.mcp.repl.ReplManager
import dev.rnett.gradle.mcp.repl.ReplRequest
import dev.rnett.gradle.mcp.repl.ReplResponse
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.ImageContent
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.ExperimentalTime

class ReplTools(
    val gradle: GradleProvider,
    val replManager: ReplManager,
) : McpServerComponent("REPL Tools", "Tools for interacting with a Kotlin REPL session.") {

    @Serializable
    enum class ReplCommand {
        @Description("Starts (or replaces) a REPL session. Requires projectPath and sourceSet.")
        start,

        @Description("Stops the current REPL session.")
        stop,

        @Description("Sends a Kotlin code snippet to the current REPL session to execute. Requires code.")
        run
    }

    @Serializable
    data class ReplArgs(
        val command: ReplCommand,
        val projectRoot: GradleProjectRootInput = GradleProjectRootInput.DEFAULT,
        @Description("The Gradle project path (e.g., ':app'). Required for 'start'.")
        val projectPath: String? = null,
        @Description("The source set to use (e.g., 'main'). Required for 'start'.")
        val sourceSet: String? = null,
        @Description("The Kotlin code snippet to execute. Required for 'run'.")
        val code: String? = null
    )

    private var currentReplSessionId: String? = null

    @OptIn(ExperimentalTime::class)
    val repl by tool<ReplArgs, CallToolResult>(
        ToolNames.REPL,
        """
            |Interacts with a Kotlin REPL session. The REPL runs with the classpath and compiler configuration (plugins, args) of a Gradle source set.
            |
            |### Commands
            |- `start`: Starts a new REPL session (replacing any existing one). Requires `projectPath` (e.g., `:app`) and `sourceSet` (e.g., `main`).
            |- `stop`: Stops the currently active REPL session.
            |- `run`: Executes a Kotlin code snippet in the current session. Requires `code`.
            |
            |### Execution and Output
            |- **stdout/stderr**: Captured and returned as text.
            |- **Last Expression**: The result of the last expression in your snippet is automatically rendered.
            |- **Responder**: A `responder: dev.rnett.gradle.mcp.repl.Responder` property is available for manual output (no import necessary). Use it to return multiple items or specific formats.
            |
            |Methods on `Responder`:
            |- `respond(value: Any?, mime: String? = null)`: Manually render a value. If `mime` is null, it is automatically detected.
            |- `markdown(md: String)`: Render a markdown string.
            |- `html(fragment: String)`: Render an HTML fragment.
            |- `image(bytes: ByteArray, mime: String = "image/png")`: Render an image from bytes.
            |
            |### Automatic Rendering and Content Types
            |The tool returns a list of content items (text, images, etc.) in order of execution.
            |- Common image types (AWT `BufferedImage`, Compose `ImageBitmap`, Android `Bitmap`, or `ByteArray` with image headers) are automatically rendered as images.
            |- Markdown can be returned via `responder.markdown(md)`.
            |- HTML fragments can be returned via `responder.html(fragment)`.
            |- All other types are rendered via `toString()`.
            |- Standard out and error is also included in the tool result.
            |
            |Example using `responder`:
            |```kotlin
            |println("Generating plot...")
            |responder.image(plotBytes, "image/png")
            |println("Plot generated.")
            |"Success" // Last expression
            |```
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

        val taskPath = if (projectPath == ":") ":resolveReplEnvironment" else "$projectPath:resolveReplEnvironment"

        val result = gradle.runBuild(
            projectRoot,
            GradleInvocationArguments(
                additionalArguments = listOf(taskPath, "-Pgradle-mcp.repl.sourceSet=$sourceSet"),
                requestedInitScripts = listOf(InitScriptNames.REPL_ENV)
            ),
            { context.run { ScansTosManager.askForScansTos(projectRoot, it) } }
        ).awaitFinished()

        if (result.buildResult.isSuccessful != true) {
            return CallToolResult(listOf(TextContent("Failed to resolve REPL environment because Gradle task failed:\n${result.buildResult.toOutputString()}")), isError = true)
        }

        val output = result.buildResult.consoleOutput.toString()
        val envLines = output.lines().filter { it.contains("[gradle-mcp-repl-env]") }

        val classpath = envLines.find { it.contains("classpath=") }?.substringAfter("classpath=")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val javaExecutable = envLines.find { it.contains("javaExecutable=") }?.substringAfter("javaExecutable=")
        val compilerPlugins = envLines.find { it.contains("compilerPlugins=") }?.substringAfter("compilerPlugins=")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val compilerArgs = envLines.find { it.contains("compilerArgs=") }?.substringAfter("compilerArgs=")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()

        if (javaExecutable == null) {
            return CallToolResult(
                listOf(
                    TextContent(
                        "No JVM target available for source set '$sourceSet' in project '$projectPath'. " +
                                "Ensure that the project has a JVM target (e.g., via the `kotlin(\"jvm\")` or `java` plugin) " +
                                "and that the source set exists."
                    )
                ), isError = true
            )
        }

        val sessionId = UUID.randomUUID().toString()
        val config = ReplConfig(
            classpath = classpath,
            compilerPlugins = compilerPlugins,
            compilerArgs = compilerArgs
        )

        replManager.startSession(sessionId, config, javaExecutable)
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

    private suspend fun McpToolContext.runRepl(args: ReplArgs): CallToolResult {
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
                    textBuffer.appendLine("\n\nCompilation Error:\n${it.message}")
                }

                is ReplResponse.Result.RuntimeError -> {
                    isError = true
                    textBuffer.appendLine("\n\nRuntime Error: ${it.message}${it.stackTrace?.let { "\n$it" } ?: ""}")
                }

                is ReplResponse.Log -> {
                    // Logs are already handled by ReplManager and forwarded to the main logger.
                    // We don't need to display them in the tool output by default.
                }
            }
        }
        flushText()
        return CallToolResult(contents, isError = isError)
    }
}
