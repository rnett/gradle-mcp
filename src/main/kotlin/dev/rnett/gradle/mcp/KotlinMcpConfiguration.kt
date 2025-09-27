package dev.rnett.gradle.mcp

import dev.rnett.gradle.mcp.mcp.KotlinxJsonMapper
import dev.rnett.gradle.mcp.mcp.McpFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KotlinMcpConfiguration {
    private val toolLogger = org.slf4j.LoggerFactory.getLogger("dev.rnett.gradle.mcp.McpTool")

    @Bean
    fun json() = KotlinxJsonMapper.json

    @Bean
    fun mcpFactory() = McpFactory(json()) { request, e ->
        toolLogger.error("Exception while executing MCP request: $request", e)
    }
}