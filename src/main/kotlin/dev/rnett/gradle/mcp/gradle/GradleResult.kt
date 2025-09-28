package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.GradleConnectionException

//TODO include problems? At least the summary
sealed interface GradleResult<T> {
    val publishedScans: List<GradleBuildScan>

    data class Success<T>(val value: T, override val publishedScans: List<GradleBuildScan>) : GradleResult<T>
    data class Failure<T>(val error: GradleConnectionException, override val publishedScans: List<GradleBuildScan>) : GradleResult<T>
}

fun <T> GradleResult<T>.throwFailure(): GradleResult.Success<T> = when (this) {
    is GradleResult.Failure -> throw this.error
    is GradleResult.Success -> this
}

@Serializable
@Description("A reference to a Develocity Build Scan")
data class GradleBuildScan(
    @Description("The URL of the Build Scan. Can be used to view it.")
    val url: String,
    @Description("The Build Scan's ID")
    val id: String,
    @Description("The URL of the Develocity instance the Build Scan is located on")
    val develocityInstance: String
) {
    companion object {
        fun fromUrl(url: String): GradleBuildScan {
            val fixed = url.replace("https://gradle.com/s/", "https://scans.gradle.com/s/")
            return GradleBuildScan(fixed, fixed.substringAfter("/s/"), fixed.substringBefore("/s/"))
        }
    }
}