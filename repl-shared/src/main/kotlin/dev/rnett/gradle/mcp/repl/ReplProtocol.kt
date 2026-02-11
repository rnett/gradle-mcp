package dev.rnett.gradle.mcp.repl

import kotlinx.serialization.Serializable

@Serializable
data class ReplConfig(
    val classpath: List<String> = emptyList(),
    val compilerPlugins: List<String> = emptyList(),
    val compilerArgs: List<String> = emptyList()
)

@Serializable
data class ReplRequest(
    val code: String
)

@Serializable
sealed class ReplResponse {
    
    companion object {
        const val RPC_PREFIX = "[gradle-mcp-repl-rpc]"
    }

    @Serializable
    sealed class Output : ReplResponse() {
        abstract val data: String

        @Serializable
        data class Stdout(override val data: String) : Output()

        @Serializable
        data class Stderr(override val data: String) : Output()
    }

    @Serializable
    data class Log(
        val level: String,
        val logger: String,
        val message: String,
        val throwable: String? = null
    ) : ReplResponse()

    @Serializable
    data class Data(
        val value: String,
        val mime: String = "text/plain",
        val meta: Map<String, String> = emptyMap()
    ) : ReplResponse()

    @Serializable
    sealed class Result : ReplResponse() {
        @Serializable
        data class Success(
            val data: Data
        ) : Result()

        @Serializable
        data class CompilationError(val message: String) : Result()

        @Serializable
        data class RuntimeError(val message: String, val stackTrace: String? = null) : Result()
    }
}
