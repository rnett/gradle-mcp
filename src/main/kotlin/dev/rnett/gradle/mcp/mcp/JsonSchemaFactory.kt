package dev.rnett.gradle.mcp.mcp

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.smiley4.schemakenerator.core.CoreSteps.gettersToProperties
import io.github.smiley4.schemakenerator.core.CoreSteps.handleNameAnnotation
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.RequiredHandling
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.compileInlining
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.generateJsonSchema
import io.github.smiley4.schemakenerator.jsonschema.JsonSchemaSteps.handleCoreAnnotations
import io.github.smiley4.schemakenerator.jsonschema.data.CompiledJsonSchemaData
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.addJsonClassDiscriminatorProperty
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.analyzeTypeUsingKotlinxSerialization
import io.github.smiley4.schemakenerator.serialization.SerializationSteps.initial
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

object JsonSchemaFactory {
    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(5.minutes.toJavaDuration())
        .maximumSize(100)
        .build<SchemaInput, CompiledJsonSchemaData> { (descriptor, serializers) ->
            initial(descriptor)
                .analyzeTypeUsingKotlinxSerialization {
                    this.serializersModule = serializers
                }
                .addJsonClassDiscriminatorProperty()
                .gettersToProperties()
                .handleNameAnnotation()
                .generateJsonSchema {
                    optionals = RequiredHandling.NON_REQUIRED
                    nullables = RequiredHandling.REQUIRED
                }
                .handleCoreAnnotations()
                .compileInlining()
        }

    private data class SchemaInput(val descriptor: SerialDescriptor, val serializers: SerializersModule)

    fun generateSchema(descriptor: SerialDescriptor, serializersModule: SerializersModule = EmptySerializersModule()): CompiledJsonSchemaData {
        return cache.get(SchemaInput(descriptor, serializersModule))
    }

    fun generateSchema(serializer: KSerializer<*>, serializersModule: SerializersModule = EmptySerializersModule()): CompiledJsonSchemaData {
        return generateSchema(serializer.descriptor, serializersModule)
    }

    inline fun <reified T> generateSchema(serializersModule: SerializersModule = EmptySerializersModule()): CompiledJsonSchemaData {
        return generateSchema(serializersModule.serializer<T>().descriptor, serializersModule)
    }
}