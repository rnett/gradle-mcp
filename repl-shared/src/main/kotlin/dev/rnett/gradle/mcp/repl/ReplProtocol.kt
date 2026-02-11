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
    val code: String,
    val id: String? = null
)

@Serializable
sealed class ReplResponse {
    abstract val requestId: String?
    
    companion object {
        const val RPC_PREFIX = "[gradle-mcp-repl-rpc]"
    }

    @Serializable
    sealed class Output : ReplResponse() {
        abstract val data: String

        @Serializable
        data class Stdout(override val data: String, override val requestId: String? = null) : Output()

        @Serializable
        data class Stderr(override val data: String, override val requestId: String? = null) : Output()
    }

    @Serializable
    data class Data(
        val value: String,
        val mime: String = "text/plain",
        val meta: Map<String, String> = emptyMap(),
        override val requestId: String? = null
    ) : ReplResponse()

    @Serializable
    sealed class Result : ReplResponse() {
        @Serializable
        data class Success(
            val data: Data,
            override val requestId: String? = null
        ) : Result()

        @Serializable
        data class CompilationError(val message: String, override val requestId: String? = null) : Result()

        @Serializable
        data class RuntimeError(val message: String, val stackTrace: String? = null, override val requestId: String? = null) : Result()
    }
}
