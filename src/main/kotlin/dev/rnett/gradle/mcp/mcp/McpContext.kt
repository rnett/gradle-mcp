package dev.rnett.gradle.mcp.mcp

import io.github.smiley4.schemakenerator.jsonschema.data.CompiledJsonSchemaData
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonArray
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonBooleanValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNode
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNullValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonNumericValue
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonObject
import io.github.smiley4.schemakenerator.jsonschema.jsonDsl.JsonTextValue
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

@PublishedApi
internal val MCP_TOOL_LOGGER = LoggerFactory.getLogger("dev.rnett.gradle.mcp.McpTool")

/**
 * Converts schema-kenerator output to an MCP [ToolSchema].
 * Returns `null` if the top-level schema type is not `"object"`.
 * `$defs` from the source schema are preserved via [ToolSchema.defs].
 */
private fun CompiledJsonSchemaData.toToolSchema(): ToolSchema? {
    val obj = json.toKotlinxSerialization().jsonObject

    if (obj["type"]?.jsonPrimitive?.contentOrNull != "object") {
        return null
    }

    return ToolSchema(
        properties = obj.getValue("properties").jsonObject,
        required = obj["required"]?.jsonArray?.let { it.map { it.jsonPrimitive.content } },
        defs = obj["\$defs"]?.jsonObject
    )
}

fun CompiledJsonSchemaData.toInput(): ToolSchema =
    toToolSchema() ?: error("Object schema expected")

fun CompiledJsonSchemaData.toOutput(): ToolSchema? = toToolSchema()

fun JsonNode.toKotlinxSerialization(): JsonElement = when (this) {
    is JsonArray -> kotlinx.serialization.json.JsonArray(
        items.map { it.toKotlinxSerialization() }
    )

    is JsonObject -> {
        val props = properties.mapValues { it.value.toKotlinxSerialization() }.toMutableMap()
        // schema-kenerator emits enum schemas without a `type` field, which the MCP SDK schema model
        // rejects unless `"type": "string"` is injected. See openspec/specs/mcp-schema-simplification/spec.md
        if (props.containsKey("enum") && !props.containsKey("type")) {
            props["type"] = JsonPrimitive("string")
        }
        kotlinx.serialization.json.JsonObject(props)
    }

    is JsonBooleanValue -> JsonPrimitive(value)
    is JsonNullValue -> JsonNull
    is JsonNumericValue -> JsonPrimitive(value)
    is JsonTextValue -> JsonPrimitive(value)
}
