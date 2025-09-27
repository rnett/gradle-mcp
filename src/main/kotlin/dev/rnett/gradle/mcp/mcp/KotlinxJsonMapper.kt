package dev.rnett.gradle.mcp.mcp

import io.modelcontextprotocol.json.McpJsonMapper
import io.modelcontextprotocol.json.McpJsonMapperSupplier
import io.modelcontextprotocol.json.TypeRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class KotlinxJsonMapper(val json: Json) : McpJsonMapper {
    private fun <T> readValue(content: String, serializer: KSerializer<T>): T {
        return json.decodeFromString(serializer, content)
    }

    private fun <T : Any> serializer(type: Class<T>): KSerializer<T> {
        return json.serializersModule.serializer(type.kotlin, emptyList(), true) as KSerializer<T>
    }

    private fun <T> serializer(type: TypeRef<T>): KSerializer<T> {
        return json.serializersModule.serializer(type.type) as KSerializer<T>
    }

    override fun <T : Any> readValue(content: String, type: Class<T>): T? {
        return readValue(content, serializer(type))
    }

    override fun <T : Any> readValue(content: ByteArray, type: Class<T>): T? {
        return readValue(content.decodeToString(), serializer(type))
    }

    override fun <T : Any> readValue(content: String, type: TypeRef<T>): T? {
        return readValue(content, serializer(type))
    }

    override fun <T : Any> readValue(content: ByteArray, type: TypeRef<T>): T? {
        return readValue(content.decodeToString(), serializer(type))
    }

    override fun <T : Any> convertValue(fromValue: Any?, type: Class<T>): T? {
        if (fromValue == null) return null
        return readValue(writeValueAsString(fromValue), type)
    }

    override fun <T : Any> convertValue(fromValue: Any?, type: TypeRef<T>): T? {
        if (fromValue == null) return null
        return readValue(writeValueAsString(fromValue), type)
    }

    override fun writeValueAsString(value: Any?): String {
        return json.encodeToString(
            value
                ?.let { json.serializersModule.serializer(it::class, emptyList(), true) }
                ?: json.serializersModule.serializer<Any?>(),
            value
        )
    }

    override fun writeValueAsBytes(value: Any): ByteArray {
        return writeValueAsString(value).encodeToByteArray()
    }

    companion object {
        val json = Json {
            isLenient = true
            coerceInputValues = true
            ignoreUnknownKeys = true
            explicitNulls = true
            encodeDefaults = true
        }

        val default = KotlinxJsonMapper(json)
    }

    class DefaultSupplier() : McpJsonMapperSupplier {
        override fun get(): McpJsonMapper = default
    }


}