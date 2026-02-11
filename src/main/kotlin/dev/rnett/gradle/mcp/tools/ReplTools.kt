package dev.rnett.gradle.mcp.tools

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.mcp.McpServerComponent
import dev.rnett.gradle.mcp.repl.ReplConfig
import dev.rnett.gradle.mcp.repl.ReplManager
import io.github.smiley4.schemakenerator.core.annotations.Description
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
    val repl by tool<ReplArgs, String>(
        "repl",
        """
            |Interacts with a Kotlin REPL session, using the classpath of a Gradle source set, along with its compilation configuration (compiler plugins and args).
            |
            |Supported commands:
            |- `start`: Starts (or replaces) a REPL session for the given project and source set. Requires `projectPath` and `sourceSet`.
            |- `stop`: Stops the current REPL session.
            |- `run`: Sends a Kotlin code snippet to the current REPL session to execute. Requires `code`.
            |
            |Only one REPL session can be active at a time. Each `start` command generates a new random session ID internally and replaces any currently running session.
        """.trimMargin()
    ) {
        when (it.command) {
            ReplCommand.start -> startRepl(it)
            ReplCommand.stop -> stopRepl()
            ReplCommand.run -> runRepl(it)
        }
    }

    private suspend fun McpToolContext.startRepl(args: ReplArgs): String {
        val projectPath = args.projectPath
        val sourceSet = args.sourceSet
        if (projectPath == null || sourceSet == null) {
            isError = true
            return "projectPath and sourceSet are required for 'start' command"
        }

        val projectRoot = args.projectRoot.resolve()

        val result = gradle.runBuild(
            projectRoot,
            GradleInvocationArguments(
                additionalArguments = listOf("$projectPath:resolveReplEnvironment", "-Pgradle-mcp.repl.sourceSet=$sourceSet")
            ),
            { ScansTosManager.askForScansTos(projectRoot, it) }
        ).awaitFinished()

        if (result.buildResult.isSuccessful != true) {
            isError = true
            return "Failed to resolve REPL environment:\n${result.buildResult.toOutputString()}"
        }

        val output = result.buildResult.consoleOutput.toString()
        val envLines = output.lines().filter { it.contains("[gradle-mcp-repl-env]") }

        val classpath = envLines.find { it.contains("classpath=") }?.substringAfter("classpath=")?.split(";") ?: emptyList()
        val javaExecutable = envLines.find { it.contains("javaExecutable=") }?.substringAfter("javaExecutable=")
        val compilerPlugins = envLines.find { it.contains("compilerPlugins=") }?.substringAfter("compilerPlugins=")?.split(";") ?: emptyList()
        val compilerArgs = envLines.find { it.contains("compilerArgs=") }?.substringAfter("compilerArgs=")?.split(";") ?: emptyList()

        if (javaExecutable == null) {
            isError = true
            return "Failed to find javaExecutable in environment output"
        }

        val sessionId = UUID.randomUUID().toString()
        val config = ReplConfig(
            classpath = classpath,
            compilerPlugins = compilerPlugins,
            compilerArgs = compilerArgs
        )

        replManager.startSession(sessionId, config, javaExecutable)
        currentReplSessionId = sessionId
        return "REPL session started with ID: $sessionId"
    }

    private suspend fun stopRepl(): String {
        return currentReplSessionId?.let {
            replManager.terminateSession(it)
            currentReplSessionId = null
            "REPL session stopped."
        } ?: "No active REPL session to stop."
    }

    private suspend fun McpToolContext.runRepl(args: ReplArgs): String {
        if (args.code == null) {
            isError = true
            return "code is required for 'run' command"
        }
        if (currentReplSessionId == null) {
            isError = true
            return "No active REPL session. Start one with command 'start'."
        } else {
            TODO("Snippet execution not implemented yet")
        }
    }
}
