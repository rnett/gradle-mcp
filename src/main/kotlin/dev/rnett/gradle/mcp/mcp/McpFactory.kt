package dev.rnett.gradle.mcp.mcp

import dev.rnett.gradle.mcp.join
import dev.rnett.gradle.mcp.runCatchingExceptCancellation
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapperSupplier
import io.modelcontextprotocol.server.McpAsyncServerExchange
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.spec.McpError
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import reactor.core.publisher.Mono
import java.net.URI
import java.util.function.BiFunction

class McpFactory(val json: Json, val errorHandler: (McpSchema.Request, Throwable) -> Unit = { _, _ -> }) {

    inline fun <reified I, reified O> tool(
        name: String,
        description: String? = null,
        title: String? = null,
        readOnlyHint: Boolean? = null,
        destructiveHint: Boolean? = null,
        idempotentHint: Boolean? = null,
        openWorldHint: Boolean? = null,
        returnDirectHint: Boolean? = null,
        meta: Map<String, Any> = emptyMap(),
        noinline handler: suspend McpContext<McpSchema.CallToolRequest>.(I) -> O,
    ): List<McpServerFeatures.AsyncToolSpecification> {
        val toolDef = McpTool(
            name,
            json.serializersModule.serializer<I>(),
            json.serializersModule.serializer<O>(),
            json.serializersModule,
            title,
            description,
            readOnlyHint,
            destructiveHint,
            idempotentHint,
            openWorldHint,
            returnDirectHint,
            meta
        )

        return McpServerFeatures.AsyncToolSpecification.builder().apply {
            tool(toolDef)
            callHandler(CallHandler(handler, name, json.serializersModule.serializer<I>(), json.serializersModule.serializer<O>()))
        }.build().let { listOf(it) }
    }

    inner class CallHandler<I, O>(
        val handler: suspend McpContext<McpSchema.CallToolRequest>.(I) -> O,
        val toolName: String,
        val inputSerializer: KSerializer<I>,
        val outputSerializer: KSerializer<O>,
    ) : BiFunction<McpAsyncServerExchange, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> {
        override fun apply(exchange: McpAsyncServerExchange, request: McpSchema.CallToolRequest): Mono<McpSchema.CallToolResult> {
            return mono {
                val input = try {
                    val argumentsJson = jacksonJson.writeValueAsString(request.arguments)
                    json.decodeFromString(inputSerializer, argumentsJson)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    errorHandler(request, e)
                    return@mono McpSchema.CallToolResult.builder()
                        .addTextContent("Error parsing input for tool $toolName: ${e.message}")
                        .isError(true).build()
                }

                val output = McpContext(exchange, request).use { runCatchingExceptCancellation { handler(it, input) } }

                if (output.isSuccess) {
                    val value = output.getOrThrow()

                    if (value is McpSchema.Content) {
                        return@mono McpSchema.CallToolResult.builder()
                            .content(listOf(value))
                            .build()
                    }

                    if (value is McpSchema.CallToolResult) {
                        return@mono value
                    }

                    if (value is Unit) {
                        return@mono McpSchema.CallToolResult.builder().addTextContent("Done").build()
                    }

                    return@mono McpSchema.CallToolResult.builder()
                        .structuredContent(jacksonJson.readValue(json.encodeToString(outputSerializer, value), Object::class.java))
                        .build()
                } else {
                    errorHandler(request, output.exceptionOrNull()!!)
                    if (output.exceptionOrNull() is McpError) {
                        throw output.exceptionOrNull()!!
                    }
                    return@mono McpSchema.CallToolResult.builder()
                        .isError(true)
                        .addTextContent("Error invoking tool $toolName: ${output.exceptionOrNull()!!.message}")
                        .build()
                }
            }
        }
    }

    fun resource(
        uri: String,
        name: String,
        description: String? = null,
        title: String? = null,
        mimeType: String? = null,
        size: Long? = null,
        annotations: McpSchema.Annotations? = null,
        meta: Map<String, Any> = emptyMap(),
        handler: suspend McpContext<McpSchema.ReadResourceRequest>.(URI) -> List<McpSchema.ResourceContents>?
    ): List<McpServerFeatures.AsyncResourceSpecification> {
        return McpServerFeatures.AsyncResourceSpecification(
            McpResource(uri, name, description, title, mimeType, size, annotations, meta)
        ) { exchange, request ->
            mono {
                val output = McpContext(exchange, request).use { runCatchingExceptCancellation { handler(it, URI.create(uri)) } }

                if (output.isSuccess) {
                    if (output.getOrNull() == null) {
                        throw McpError.RESOURCE_NOT_FOUND.apply(request.uri)
                    }

                    McpSchema.ReadResourceResult(
                        output.getOrNull()!!
                    )
                } else {
                    wrapError(request, output.exceptionOrNull()!!)
                }
            }
        }.let { listOf(it) }
    }

    fun prompt(
        name: String,
        arguments: List<McpSchema.PromptArgument>,
        description: String? = null,
        title: String? = null,
        meta: Map<String, Any> = emptyMap(),
        handler: suspend McpContext<McpSchema.GetPromptRequest>.(Map<String, String>) -> McpSchema.GetPromptResult
    ): List<McpServerFeatures.AsyncPromptSpecification> {
        return McpServerFeatures.AsyncPromptSpecification(McpPrompt(name, arguments, description, title, meta)) { exchange, request ->
            mono {
                val output = McpContext(exchange, request).use {
                    runCatchingExceptCancellation { handler(it, request.arguments.mapValues { jacksonJson.writeValueAsString(it.value) }) }
                }

                if (output.isSuccess) {
                    output.getOrNull()
                } else {
                    wrapError(request, output.exceptionOrNull()!!)
                }
            }
        }.let { listOf(it) }
    }

    fun completion(
        reference: McpSchema.CompleteReference,
        handler: suspend McpContext<McpSchema.CompleteRequest>.(McpSchema.CompleteRequest.CompleteArgument) -> McpSchema.CompleteResult.CompleteCompletion
    ): List<McpServerFeatures.AsyncCompletionSpecification> {
        return McpServerFeatures.AsyncCompletionSpecification(reference) { exchange, request ->
            mono {
                val output = McpContext(exchange, request).use {
                    runCatchingExceptCancellation { handler(it, request.argument) }
                }

                if (output.isSuccess) {
                    output.getOrNull()!!.let { McpSchema.CompleteResult(it) }
                } else {
                    wrapError(request, output.exceptionOrNull()!!)
                }
            }
        }.let { listOf(it) }
    }

    private fun wrapError(resuest: McpSchema.Request, error: Throwable): Nothing {
        if (error is CancellationException) {
            throw error
        }
        errorHandler.invoke(resuest, error)
        if (error is McpError) {
            throw error
        } else {
            val code = when (error) {
                is SerializationException -> McpSchema.ErrorCodes.PARSE_ERROR
                is IllegalArgumentException -> McpSchema.ErrorCodes.INVALID_REQUEST
                else -> McpSchema.ErrorCodes.INTERNAL_ERROR
            }
            throw McpError.builder(code)
                .message(error.message)
                .build()
        }
    }

    class McpContext<R : McpSchema.Request>(
        val exchange: McpAsyncServerExchange,
        val request: R
    ) : AutoCloseable {

        val meta: Map<String, Any?> = request.meta().orEmpty()
        val progressToken: String? = request.progressToken()


        data class LoggingNotification(val level: McpSchema.LoggingLevel, val logger: String, val message: String, val meta: Map<String, Any?> = emptyMap())
        data class ProgressNotification(val progress: Double?, val total: Double?, val message: String?, val meta: Map<String, Any?> = emptyMap())

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val loggingMessages: MutableSharedFlow<LoggingNotification> = MutableSharedFlow(0, 50, BufferOverflow.DROP_OLDEST)
        val progressNotifications: MutableSharedFlow<ProgressNotification> = MutableSharedFlow(0, 50, BufferOverflow.DROP_OLDEST)

        init {
            scope.launch {
                loggingMessages.collect { awaitSendLoggingNotification(it) }
            }
            if (progressToken != null) {
                scope.launch {
                    progressNotifications.collect { awaitSendProgressNotification(it) }
                }
            }
        }

        fun emitLoggingNotification(
            logger: String,
            level: McpSchema.LoggingLevel,
            message: String,
            meta: Map<String, Any?> = emptyMap()
        ) {
            loggingMessages.tryEmit(LoggingNotification(level, logger, message, meta))
        }

        fun emitProgressNotification(
            progress: Double?,
            total: Double?,
            message: String?,
            meta: Map<String, Any?> = emptyMap()
        ) {
            if (progress != null) {
                progressNotifications.tryEmit(ProgressNotification(progress, total, message, meta))
            }
        }

        suspend fun awaitSendLoggingNotification(
            notification: LoggingNotification
        ) {
            withContext(Dispatchers.IO) {
                exchange.loggingNotification(
                    McpSchema.LoggingMessageNotification(
                        notification.level,
                        notification.logger,
                        notification.message,
                        notification.meta
                    )
                ).join()
            }
        }

        suspend fun awaitSendProgressNotification(
            notification: ProgressNotification
        ) {
            if (progressToken != null) {
                withContext(Dispatchers.IO) {
                    exchange.progressNotification(
                        McpSchema.ProgressNotification(
                            progressToken,
                            notification.progress,
                            notification.total,
                            notification.message,
                            notification.meta
                        )
                    ).join()
                }
            }
        }

        override fun close() {
            scope.cancel()
        }
    }

    @PublishedApi
    internal val jacksonJson = JacksonMcpJsonMapperSupplier().get()

    fun McpTool(
        name: String,
        inputSerializer: KSerializer<*>,
        outputSerializer: KSerializer<*>? = null,
        serializersModule: SerializersModule = EmptySerializersModule(),
        title: String? = null,
        description: String? = null,
        readOnlyHint: Boolean? = null,
        destructiveHint: Boolean? = null,
        idempotentHint: Boolean? = null,
        openWorldHint: Boolean? = null,
        returnDirectHint: Boolean? = null,
        meta: Map<String, Any> = emptyMap()
    ): McpSchema.Tool {

        val inputSchema = JsonSchemaFactory.generateSchema(inputSerializer, serializersModule)
        val outputSchema = outputSerializer?.let { JsonSchemaFactory.generateSchema(it, serializersModule) }

        return McpSchema.Tool.builder().apply {
            name(name)
            title(title)
            description(description)
            annotations(
                McpSchema.ToolAnnotations(
                    title,
                    readOnlyHint,
                    destructiveHint,
                    idempotentHint,
                    openWorldHint,
                    returnDirectHint
                )
            )
            meta(meta)
            inputSchema(jacksonJson, inputSchema.json.prettyPrint())
            if (outputSchema != null)
                outputSchema(jacksonJson, outputSchema.json.prettyPrint())

        }.build()
    }

    fun Annotations(
        audience: List<McpSchema.Role>? = null,
        priority: Double? = null,
        lastModified: String? = null
    ): McpSchema.Annotations {
        return McpSchema.Annotations(audience, priority, lastModified)
    }

    fun McpResource(
        uri: String,
        name: String,
        description: String? = null,
        title: String? = null,
        mimeType: String? = null,
        size: Long? = null,
        annotations: McpSchema.Annotations? = null,
        meta: Map<String, Any?> = emptyMap()
    ): McpSchema.Resource {
        return McpSchema.Resource.builder().apply {
            uri(uri)
            name(name)
            description?.let(::description)
            title?.let(::title)
            mimeType?.let(::mimeType)
            size?.let(::size)
            annotations?.let(::annotations)
            meta(meta)
        }.build()
    }

    fun McpResourceTemplate(
        uriTemplate: String,
        name: String,
        description: String? = null,
        title: String? = null,
        mimeType: String? = null,
        annotations: McpSchema.Annotations? = null,
        meta: Map<String, Any> = emptyMap(),
    ): McpSchema.ResourceTemplate {
        return McpSchema.ResourceTemplate(
            uriTemplate,
            name,
            title,
            description,
            mimeType,
            annotations,
            meta
        )
    }

    fun McpPromptArgument(name: String, required: Boolean, description: String? = null, title: String? = null) = McpSchema.PromptArgument(name, title, description, required)

    fun McpPrompt(
        name: String,
        arguments: List<McpSchema.PromptArgument>,
        description: String? = null,
        title: String? = null,
        meta: Map<String, Any> = emptyMap()
    ): McpSchema.Prompt = McpSchema.Prompt(
        name,
        title,
        description,
        arguments,
        meta
    )

    fun McpPromptReference(type: String, name: String, title: String? = null): McpSchema.PromptReference = McpSchema.PromptReference(type, name, title)
    fun McpResourceReference(type: String, uri: String): McpSchema.ResourceReference = McpSchema.ResourceReference(type, uri)
}