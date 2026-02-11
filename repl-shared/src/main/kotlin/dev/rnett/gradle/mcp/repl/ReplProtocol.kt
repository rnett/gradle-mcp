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
    @Serializable
    data class Output(val event: String, val data: String) : ReplResponse()

    @Serializable
    data class Display(
        val kind: String,
        val data: String,
        val mime: String? = null,
        val meta: Map<String, String> = emptyMap()
    ) : ReplResponse()

    @Serializable
    data class Result(
        val success: Boolean,
        val data: String,
        val renderKind: String? = null,
        val mime: String? = null
    ) : ReplResponse()
}
