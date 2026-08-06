package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.tools.GradlePathUtils
import dev.rnett.gradle.mcp.utils.EnvProvider
import dev.rnett.gradle.mcp.utils.OS
import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Example
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.gradle.tooling.model.ProjectIdentifier
import kotlin.io.path.absolute

/**
 * The Gradle CLI option that disables interactive console prompting.
 *
 * Gradle does not honor a `NONINTERACTIVE` environment variable; this option must be passed on
 * the command line.
 */
internal const val NON_INTERACTIVE_ARG = "--non-interactive"

/**
 * The first Gradle version that supports the [NON_INTERACTIVE_ARG] option. Passing the option to
 * an older version would fail the build with an unknown-option error.
 */
internal const val NON_INTERACTIVE_MIN_GRADLE_VERSION = "9.6.0"

/**
 * Whether the given Gradle version supports the `--non-interactive` CLI option, i.e. whether its
 * numeric base version (the part before any `-pre`/`+build` suffix) is at least
 * [NON_INTERACTIVE_MIN_GRADLE_VERSION]. Unparseable versions report `false` so callers fall back
 * to not passing the flag.
 */
internal fun String.supportsNonInteractiveMode(): Boolean {
    val base = substringBefore('-').substringBefore('+')
    val parts = base.split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0

    val minParts = NON_INTERACTIVE_MIN_GRADLE_VERSION.split('.')
    val minMajor = minParts[0].toInt()
    val minMinor = minParts[1].toInt()

    return major > minMajor || (major == minMajor && minor >= minMinor)
}

fun ProjectIdentifier.matches(root: GradleProjectRoot, projectPath: GradleProjectPath): Boolean =
    buildIdentifier.rootDir.toPath().absolute() == GradlePathUtils.getRootProjectPath(root) && this.projectPath == projectPath.path

@Serializable
@Description("Additional arguments to configure the Gradle process.")
data class GradleInvocationArguments(
    /**
     * Additional environment variables to set for the Gradle process. 
     * These will be merged with the inherited environment according to [envSource].
     */
    @Description("Additional environment variables to set for the Gradle process. Optional.")
    val additionalEnvVars: Map<String, String> = emptyMap(),

    /**
     * Additional system properties (passed via -D) to set for the Gradle process.
     */
    @Description("Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server.")
    val additionalSystemProps: Map<String, String> = emptyMap(),

    /**
     * Additional JVM arguments to set for the Gradle process.
     */
    @Description("Additional JVM arguments to set for the Gradle process. Optional.")
    val additionalJvmArgs: List<String> = emptyList(),

    /**
     * Raw command line arguments to pass to the Gradle process.
     */
    @Description("Additional arguments for the Gradle process. Optional.")
    val additionalArguments: List<String> = emptyList(),

    /**
     * Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument.
     */
    @Description("Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation.")
    val publishScan: Boolean = false,

    /**
     * Specifies where to inherit base environment variables from.
     */
    @Description("Where to get the environment variables from to pass to Gradle. Defaults to INHERIT. SHELL starts a new shell process and queries its env vars. Recommended if Gradle isn't finding environment variables (e.g. for JDKs) that should be present, which can happen if the host process starts before the shell environment is fully loaded.")
    val envSource: EnvSource = EnvSource.INHERIT,

    /**
     * The path to the Java home directory to use for the Gradle daemon.
     *
     * When provided, it takes precedence over the project's daemon JVM settings
     * (`gradle/gradle-daemon-jvm.properties` and `org.gradle.java.home`) and over `JAVA_HOME` from
     * the environment — even if those settings contain invalid values, because the same home is
     * also passed as an `org.gradle.java.home` build argument; if the path is not a valid
     * directory, no other JVM source is promoted (the launcher omits `setJavaHome` and Gradle's own
     * daemon JVM resolution applies).
     *
     * When omitted, the project's daemon JVM settings take precedence, then `JAVA_HOME` from the
     * environment (see [envSource]), then the Tooling API default.
     */
    @Description("The path to the Java home directory to use for the Gradle daemon. When provided, it takes precedence over the project's daemon JVM settings (gradle/gradle-daemon-jvm.properties and org.gradle.java.home) and over JAVA_HOME from the environment, even if those settings contain invalid values; if the path is invalid no other JVM source is promoted. When omitted, the project's daemon JVM settings take precedence, then JAVA_HOME from the environment (see envSource), then the Tooling API default.")
    val javaHome: String? = null,

    /**
     * Internal list of init script names to be loaded.
     */
    @Description("The names of the init scripts to load. Defaults to empty list.")
    @Transient
    val requestedInitScripts: List<String> = emptyList()
) {
    operator fun plus(other: GradleInvocationArguments): GradleInvocationArguments {
        return GradleInvocationArguments(
            additionalEnvVars = additionalEnvVars + other.additionalEnvVars,
            additionalSystemProps = additionalSystemProps + other.additionalSystemProps,
            additionalJvmArgs = additionalJvmArgs + other.additionalJvmArgs,
            additionalArguments = additionalArguments + other.additionalArguments,
            publishScan = publishScan || other.publishScan,
            envSource = if (other.envSource != EnvSource.INHERIT) other.envSource else envSource,
            javaHome = other.javaHome ?: javaHome,
            requestedInitScripts = requestedInitScripts + other.requestedInitScripts
        )
    }

    fun renderCommandLine(): String = buildString {
        if (javaHome != null) {
            append("JAVA_HOME=$javaHome ")
        }
        additionalEnvVars.forEach { (k, v) ->
            append("$k=$v ")
        }
        if (additionalJvmArgs.isNotEmpty() || additionalSystemProps.isNotEmpty()) {
            append("java ")
            additionalJvmArgs.forEach { a ->
                append("$a ")
            }
            additionalSystemProps.forEach { (k, v) ->
                append("-D$k=$v ")
            }
        }
        append("gradle ")
        allAdditionalArguments.forEach { a ->
            append(a).append(" ")
        }
    }.trim()

    fun actualEnvVars(envProvider: EnvProvider): Map<String, String> {
        val base = when (envSource) {
            EnvSource.NONE -> emptyMap()
            EnvSource.INHERIT -> envProvider.getInheritedEnvironment()
            EnvSource.SHELL -> envProvider.getShellEnvironment()
        }

        if (additionalEnvVars.isEmpty()) return base

        if (!OS.isWindows) return base + additionalEnvVars

        val result = base.toMutableMap()
        additionalEnvVars.forEach { (k, v) ->
            val existingKey = result.keys.find { it.equals(k, ignoreCase = true) }
            if (existingKey != null) {
                result.remove(existingKey)
            }
            result[k] = v
        }
        return result
    }

    @Transient
    val allAdditionalArguments = additionalArguments +
            (if (publishScan && "--scan" !in additionalArguments) listOf("--scan") else emptyList())

    /**
     * Returns a copy of these arguments with the [NON_INTERACTIVE_ARG] CLI option appended when the
     * given Gradle version supports it. Gradle only accepts `--non-interactive` from version
     * [NON_INTERACTIVE_MIN_GRADLE_VERSION] onwards, and passing an unknown option to older versions
     * would fail the build, so the flag is omitted for older or undetectable versions. A flag that
     * is already present (passed explicitly by the caller) is never duplicated.
     */
    fun withNonInteractiveIfSupported(gradleVersion: String?): GradleInvocationArguments {
        if (gradleVersion == null || !gradleVersion.supportsNonInteractiveMode()) return this
        if (NON_INTERACTIVE_ARG in allAdditionalArguments) return this
        return copy(additionalArguments = additionalArguments + NON_INTERACTIVE_ARG)
    }

    val isHelp: Boolean
        get() = "--help" in allAdditionalArguments || "-h" in allAdditionalArguments

    val isVersion: Boolean
        get() = "--version" in allAdditionalArguments || "-v" in allAdditionalArguments

    fun withInitScript(name: String) = copy(requestedInitScripts = requestedInitScripts + name)

    companion object {
        val DEFAULT = GradleInvocationArguments()
    }
}

@Serializable
enum class EnvSource {
    NONE,
    INHERIT,
    SHELL
}

@JvmInline
@Serializable
@Description("The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located.")
value class GradleProjectRoot(val projectRoot: String)

@JvmInline
@Serializable
@Description("The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'")
@Example(":")
@Example(":my-project")
@Example(":my-project:subproject")
value class GradleProjectPath(private val projectPath: String) {
    companion object {
        val DEFAULT = GradleProjectPath(":")
    }

    val path get() = ':' + projectPath.trim(':')

    val isRootProject get() = projectPath.isBlank() || projectPath == ":"

    fun taskPath(task: String): String = buildString {
        append(path)
        if (!isRootProject)
            append(':')
        append(task.trimStart(':'))
    }

    override fun toString(): String {
        return path
    }
}