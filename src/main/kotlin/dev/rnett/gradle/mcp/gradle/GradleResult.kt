package dev.rnett.gradle.mcp.gradle

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import kotlin.time.Duration

//TODO include problems? At least the summary
data class GradleResult<out T>(
    val publishedScans: List<GradleBuildScan>,
    val testResults: TestResults?,
    val outcome: Outcome<T>
) {

    sealed interface Outcome<out T>

    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: GradleConnectionException) : Outcome<Nothing>
}

data class TestResults(
    val passed: Set<TestResult>,
    val skipped: Set<TestResult>,
    val failed: Set<TestResult>,
) {
    val isEmpty: Boolean get() = passed.isEmpty() && skipped.isEmpty() && failed.isEmpty()
}

data class TestResult(
    val testName: String,
    val output: String?,
    val duration: Duration,
    val failures: List<Failure>?
)

fun <T> GradleResult.Outcome<T>.throwFailure(): GradleResult.Success<T> = when (this) {
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