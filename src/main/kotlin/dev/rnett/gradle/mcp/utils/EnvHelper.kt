package dev.rnett.gradle.mcp.utils

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

interface EnvProvider {
    fun getShellEnvironment(): Map<String, String>
    fun getInheritedEnvironment(): Map<String, String> = System.getenv()
}

object DefaultEnvProvider : EnvProvider {
    private val LOGGER = LoggerFactory.getLogger(DefaultEnvProvider::class.java)

    private fun parseEnvironment(output: String): Map<String, String> {
        val env = mutableMapOf<String, String>()
        output.lineSequence().forEach { line ->
            val index = line.indexOf('=')
            if (index != -1) {
                val key = line.substring(0, index)
                val value = line.substring(index + 1)
                env[key] = value
            }
        }
        return env
    }

    override fun getShellEnvironment(): Map<String, String> {
        LOGGER.trace("Detecting environment from shell")
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val command = if (isWindows) {
            listOf("cmd.exe", "/c", "set")
        } else {
            val shell = System.getenv("SHELL") ?: "sh"
            listOf(shell, "-c", "env")
        }

        return try {
            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val env = parseEnvironment(output)

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                LOGGER.warn("Shell environment detection failed with exit code $exitCode. Falling back to inherited environment.")
                getInheritedEnvironment()
            } else {
                LOGGER.trace("Detected ${env.size} environment variables from shell")
                env
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to detect environment from shell: ${e.message}. Falling back to inherited environment.")
            getInheritedEnvironment()
        }
    }
}

object EnvHelper : EnvProvider {
    override fun getShellEnvironment(): Map<String, String> = DefaultEnvProvider.getShellEnvironment()
    override fun getInheritedEnvironment(): Map<String, String> = DefaultEnvProvider.getInheritedEnvironment()
}