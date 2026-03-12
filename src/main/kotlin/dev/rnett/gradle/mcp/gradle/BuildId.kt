package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
@Description("An ID uniquely identifying a build. Used to look up more information about it later using this MCP server. The build ID does not exist outside of this server. Builds expire 60m after they are last accessed.")
value class BuildId(val id: String) {

    companion object {
        fun parse(text: String): BuildId {
            return BuildId(text)
        }
    }

    override fun toString(): String {
        return id
    }
}
