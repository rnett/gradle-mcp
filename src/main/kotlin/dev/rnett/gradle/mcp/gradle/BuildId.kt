package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable(with = BuildId.Serializer::class)
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Description("An ID uniquely identifying a build. Used to look up more information about it later using this MCP server. The build ID does not exist outside of this server. Builds expire 10m after they are last accessed.")
class BuildId(val id: Uuid, val timestamp: Instant) {

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun newId() = BuildId(Uuid.Companion.random(), Clock.System.now())

        fun parse(text: String): BuildId {
            val parts = text.split("@")
            require(parts.size == 2) { "Build ID must be in format \"<uuid>@<timestamp>\"" }
            return BuildId(Uuid.Companion.parse(parts[0]), Instant.Companion.parse(parts[1]))
        }
    }

    override fun toString(): String {
        return id.toString() + "@" + timestamp.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BuildId

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    object Serializer : KSerializer<BuildId> {
        private val delegate = String.Companion.serializer()
        override val descriptor: SerialDescriptor = SerialDescriptor("dev.rnett.gradle.mcp.gradle.BuildId", delegate.descriptor)

        override fun serialize(encoder: Encoder, value: BuildId) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): BuildId {
            return BuildId.parse(decoder.decodeString())
        }

    }
}