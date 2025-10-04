package dev.rnett.gradle.mcp.tools

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import dev.rnett.gradle.mcp.gradle.GradleProjectRoot
import dev.rnett.gradle.mcp.gradle.GradleScanTosAcceptRequest
import dev.rnett.gradle.mcp.mcp.ElicitationResult
import dev.rnett.gradle.mcp.mcp.McpContext
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

object ScansTosManager {
    private val cache = Caffeine.newBuilder()
        .expireAfter(Expiry.creating<GradleProjectRoot, Duration> { _, v -> v.toJavaDuration() })
        .maximumSize(100)
        .build<GradleProjectRoot, Duration>()

    @Serializable
    data class ElicicationResponse(
        @Description("How long to remember the acceptance for, in minutes. Only applies to this project.  Max 60. Default is 0.")
        val rememberForMinutes: Int = 0
    )

    @PublishedApi
    context(ctx: McpContext)
    internal suspend fun askForScansTos(buildRoot: GradleProjectRoot, tosAcceptRequest: GradleScanTosAcceptRequest): Boolean {

        val existing = cache.getIfPresent(buildRoot)
        if (existing != null) {
            return true
        }

        val result = ctx.elicit<ElicicationResponse>(tosAcceptRequest.fullMessage, GradleScanTosAcceptRequest.TIMEOUT)

        if (result is ElicitationResult.Accept) {
            val remember = result.data.rememberForMinutes
            if (remember > 0) {
                cache.put(buildRoot, remember.coerceAtMost(60).minutes)
            }
            return true
        }
        return false
    }
}