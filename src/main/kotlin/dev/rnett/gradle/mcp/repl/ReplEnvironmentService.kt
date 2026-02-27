package dev.rnett.gradle.mcp.repl

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.tools.InitScriptNames
import dev.rnett.gradle.mcp.tools.toOutputString
import org.slf4j.LoggerFactory

interface ReplEnvironmentService {
    suspend fun resolveReplEnvironment(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        sourceSet: String,
        additionalDependencies: List<String>
    ): ReplConfigWithJava
}

data class ReplConfigWithJava(
    val config: ReplConfig,
    val javaExecutable: String
)

class DefaultReplEnvironmentService(private val gradle: GradleProvider) : ReplEnvironmentService {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DefaultReplEnvironmentService::class.java)
        private const val ENV_MARKER = "[gradle-mcp-repl-env]"
    }

    override suspend fun resolveReplEnvironment(
        projectRoot: GradleProjectRoot,
        projectPath: String,
        sourceSet: String,
        additionalDependencies: List<String>
    ): ReplConfigWithJava {
        val taskPath = if (projectPath == ":") ":resolveReplEnvironment" else "$projectPath:resolveReplEnvironment"

        val running = gradle.runBuild(
            projectRoot,
            GradleInvocationArguments(
                additionalArguments = listOf(
                    taskPath,
                    "-Pgradle-mcp.repl.project=$projectPath",
                    "-Pgradle-mcp.repl.sourceSet=$sourceSet",
                    "-Pgradle-mcp.repl.additionalDependencies=${additionalDependencies.joinToString(";|;")}",
                ),
                requestedInitScripts = listOf(InitScriptNames.REPL_ENV)
            ),
            { false }
        )

        val finished = running.awaitFinished()
        if (finished.outcome !is BuildOutcome.Success) {
            throw RuntimeException("Failed to resolve REPL environment because Gradle task failed:\n${finished.toOutputString()}")
        }

        val output = finished.consoleOutput.toString()
        val envLines = output.lines().filter { it.contains(ENV_MARKER) }

        val classpath = envLines.find { it.contains("classpath=") }?.substringAfter("classpath=")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val javaExecutable = envLines.find { it.contains("javaExecutable=") }?.substringAfter("javaExecutable=")
        val pluginsClasspath = envLines.find { it.contains("pluginsClasspath=") }?.substringAfter("pluginsClasspath=")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val compilerPluginOptionsString = envLines.find { it.contains("compilerPluginOptions=") }?.substringAfter("compilerPluginOptions=")
        val compilerPluginOptionsList = compilerPluginOptionsString?.split(";")?.filter { it.isNotBlank() } ?: emptyList()
        val compilerArgs = envLines.find { it.contains("compilerArgs=") }?.substringAfter("compilerArgs=")?.split(";")?.filter { it.isNotBlank() } ?: emptyList()

        if (javaExecutable == null) {
            throw RuntimeException(
                "No JVM target available for source set '$sourceSet' in project '$projectPath'. " +
                        "Ensure that the project has a JVM target (e.g., via the `kotlin(\"jvm\")` or `java` plugin) " +
                        "and that the source set exists."
            )
        }

        val config = ReplConfig(
            classpath = classpath,
            pluginsClasspath = pluginsClasspath,
            compilerPluginOptions = compilerPluginOptionsList.mapNotNull {
                // Expected format: pluginId:optionName=value
                val parts = it.split(":", limit = 2)
                if (parts.size == 2) {
                    val pluginId = parts[0]
                    val optionParts = parts[1].split("=", limit = 2)
                    if (optionParts.size == 2) {
                        KotlinCompilerPluginOption(pluginId, optionParts[0], optionParts[1])
                    } else null
                } else null
            },
            compilerArgs = compilerArgs
        )

        return ReplConfigWithJava(config, javaExecutable)
    }
}
