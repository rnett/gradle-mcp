package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import dev.rnett.gradle.mcp.utils.OS
import org.gradle.tooling.ConfigurableLauncher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * Server-process system property consulted when resolving the effective Gradle user home.
 *
 * Gradle's real client-side key is `gradle.user.home`; the historical `org.gradle.user.home` key
 * is a latent defect — Gradle ignores it, so it must never be used as the resolution channel.
 */
internal const val GRADLE_USER_HOME_SYS_PROP = "gradle.user.home"

/** Server-process environment variable consulted when resolving the effective Gradle user home. */
internal const val GRADLE_USER_HOME_ENV = "GRADLE_USER_HOME"

/** The environment variable key used as a Java-home fallback by [DaemonJvmSelector]. */
internal const val JAVA_HOME_ENV = "JAVA_HOME"

/**
 * Build argument (raw `-D` launcher argument) that enforces an explicit javaHome as the daemon JVM
 * source. See [configureJavaHome] for the Gradle 9.6.1 mechanics that make pairing this argument
 * with [ConfigurableLauncher.setJavaHome] safe.
 */
internal const val ORG_GRADLE_JAVA_HOME_ARG = "-Dorg.gradle.java.home="

/**
 * The Gradle daemon JVM settings the detector recognizes, i.e. the settings Gradle itself applies
 * to the current project. The detector answers *whether* any such setting exists; Gradle remains
 * responsible for choosing among them (criteria precedence, user-home-over-project properties).
 */
sealed interface DaemonJvmSettingSource {
    /** S1: `<projectRoot>/gradle/gradle-daemon-jvm.properties` with an effective `toolchainVersion`. */
    data class DaemonJvmCriteria(val file: Path, val toolchainVersion: String) : DaemonJvmSettingSource

    /** S2/S3: `org.gradle.java.home` in a `gradle.properties` file. */
    data class OrgGradleJavaHome(val file: Path, val scope: Scope) : DaemonJvmSettingSource {
        enum class Scope { USER_HOME, PROJECT }
    }

    /**
     * Fail-closed marker: the file exists but could not be read or parsed, so settings are treated
     * as present and the caller defers to Gradle rather than promoting a lower-priority source.
     */
    data class Unparseable(val file: Path, val cause: String) : DaemonJvmSettingSource

    /**
     * Fail-closed marker: the file declares a daemon JVM setting with an invalid (blank) value.
     * Probe V2 showed Gradle rejects blank values (`Value '' given for toolchainVersion is an
     * invalid Java version` / `Value '' given for org.gradle.java.home Gradle property is
     * invalid`), so a blank value is treated as settings present rather than absent.
     */
    data class InvalidValue(val file: Path, val key: String, val value: String) : DaemonJvmSettingSource
}

/**
 * The outcome of daemon-settings detection. [settingsPresent] is true when any effective source
 * (or a fail-closed [DaemonJvmSettingSource.Unparseable] file) was found; the caller then omits
 * `setJavaHome` and lets Gradle apply its own daemon JVM chain.
 */
data class DaemonJvmSettingsDetection(
    val settingsPresent: Boolean,
    val sources: List<DaemonJvmSettingSource>,
    val diagnostics: List<String>
) {
    companion object {
        val ABSENT = DaemonJvmSettingsDetection(false, emptyList(), emptyList())
    }
}

/**
 * Detects the project daemon JVM settings ([DaemonJvmSettingSource]) that must suppress environment
 * `JAVA_HOME` promotion. Detection is pure and stateless: files are read on every call (no caching),
 * and all diagnostics are returned to the caller for emission.
 *
 * Sources, in the order Gradle applies them:
 *  - S1: `<projectRoot>/gradle/gradle-daemon-jvm.properties` with an effective `toolchainVersion`;
 *  - S2: `org.gradle.java.home` in `<effectiveGradleUserHome>/gradle.properties`;
 *  - S3: `org.gradle.java.home` in `<projectRoot>/gradle.properties`.
 *
 * The distribution-level `<GRADLE_HOME>/gradle.properties` is deliberately excluded: the effective
 * wrapper distribution is resolved later by the Tooling API, so consulting a potentially unrelated
 * `GRADLE_HOME` could suppress environment `JAVA_HOME` for a setting the build never sees.
 *
 * Missing files fail open. Existing files that cannot be read/parsed ([DaemonJvmSettingSource.Unparseable])
 * and present-but-blank values ([DaemonJvmSettingSource.InvalidValue], per probe V2) fail closed:
 * Gradle rejects both, so promoting environment `JAVA_HOME` would mask configuration Gradle is
 * about to reject.
 */
open class DaemonJvmSettingsDetector {
    open fun detect(projectRoot: Path, gradleUserHome: Path): DaemonJvmSettingsDetection {
        val sources = mutableListOf<DaemonJvmSettingSource>()
        val diagnostics = mutableListOf<String>()

        detectDaemonJvmCriteria(
            projectRoot.resolve("gradle").resolve("gradle-daemon-jvm.properties"),
            sources,
            diagnostics
        )
        detectOrgGradleJavaHome(
            gradleUserHome.resolve("gradle.properties"),
            DaemonJvmSettingSource.OrgGradleJavaHome.Scope.USER_HOME,
            sources,
            diagnostics
        )
        detectOrgGradleJavaHome(
            projectRoot.resolve("gradle.properties"),
            DaemonJvmSettingSource.OrgGradleJavaHome.Scope.PROJECT,
            sources,
            diagnostics
        )

        return DaemonJvmSettingsDetection(sources.isNotEmpty(), sources, diagnostics)
    }

    private fun detectDaemonJvmCriteria(
        file: Path,
        sources: MutableList<DaemonJvmSettingSource>,
        diagnostics: MutableList<String>
    ) {
        val properties = loadProperties(file, sources, diagnostics) ?: return
        val raw = properties.getProperty("toolchainVersion")
        if (raw == null) return
        val version = raw.trim()
        if (version.isBlank()) {
            // Probe V2: Gradle rejects a blank toolchainVersion ("Value '' given for toolchainVersion
            // is an invalid Java version"), so a blank value fails closed (settings present).
            sources += DaemonJvmSettingSource.InvalidValue(file, "toolchainVersion", version)
            diagnostics += "Daemon JVM criteria file $file has a blank toolchainVersion; Gradle rejects blank values, treating daemon JVM settings as present."
        } else {
            sources += DaemonJvmSettingSource.DaemonJvmCriteria(file, version)
        }
    }

    private fun detectOrgGradleJavaHome(
        file: Path,
        scope: DaemonJvmSettingSource.OrgGradleJavaHome.Scope,
        sources: MutableList<DaemonJvmSettingSource>,
        diagnostics: MutableList<String>
    ) {
        val properties = loadProperties(file, sources, diagnostics) ?: return
        val raw = properties.getProperty("org.gradle.java.home")
        if (raw == null) return
        val value = raw.trim()
        if (value.isBlank()) {
            // Probe V2: Gradle rejects a blank org.gradle.java.home ("Value '' given for
            // org.gradle.java.home Gradle property is invalid"), so a blank value fails closed.
            sources += DaemonJvmSettingSource.InvalidValue(file, "org.gradle.java.home", value)
            diagnostics += "Gradle properties file $file has a blank org.gradle.java.home; Gradle rejects blank values, treating daemon JVM settings as present."
        } else {
            sources += DaemonJvmSettingSource.OrgGradleJavaHome(file, scope)
        }
    }

    /**
     * Loads [file] with `java.util.Properties` semantics. A missing file is absent (returns null,
     * no diagnostics); an existing file that cannot be read or parsed fails closed: it becomes an
     * [DaemonJvmSettingSource.Unparseable] source plus a diagnostic.
     */
    private fun loadProperties(
        file: Path,
        sources: MutableList<DaemonJvmSettingSource>,
        diagnostics: MutableList<String>
    ): Properties? {
        if (!Files.exists(file)) return null
        return try {
            Properties().apply {
                Files.newInputStream(file).use { load(it) }
            }
        } catch (e: Exception) {
            val cause = e.message ?: e.javaClass.simpleName
            sources += DaemonJvmSettingSource.Unparseable(file, cause)
            diagnostics += "Gradle settings file $file exists but could not be read or parsed ($cause); treating daemon JVM settings as present."
            null
        }
    }
}

/** The resolved effective Gradle user home plus the channel that supplied it and any diagnostics. */
data class ResolvedGradleUserHome(val path: Path, val channel: String, val diagnostics: List<String>)

/**
 * Resolves the effective Gradle user home used for daemon-settings detection (S2) by selecting the
 * first non-blank channel, in this exact order (final probe-V1 channel set):
 *  1. the server process system property `gradle.user.home`;
 *  2. the server process environment variable `GRADLE_USER_HOME` (case-insensitive key matching on
 *     Windows is provided natively by [System.getenv]);
 *  3. the default `<user.home>/.gradle`.
 *
 * Blank or whitespace-only values are skipped with a diagnostic. The server process inputs are
 * dependency-injected so tests do not depend on hidden process state. Invocation-level channels
 * (`org.gradle.user.home` system properties and `GRADLE_USER_HOME` in the operation environment)
 * were probed (V1) and removed because they do not reach the daemon's effective user home.
 */
class EffectiveGradleUserHomeResolver(
    private val serverSysProp: (String) -> String? = System::getProperty,
    private val serverEnv: (String) -> String? = System::getenv,
    private val defaultUserHome: () -> Path = { Paths.get(System.getProperty("user.home"), ".gradle") }
) {
    fun resolve(): ResolvedGradleUserHome {
        val diagnostics = mutableListOf<String>()

        val sysPropValue = serverSysProp(GRADLE_USER_HOME_SYS_PROP)
        if (sysPropValue != null) {
            if (sysPropValue.isNotBlank()) {
                return ResolvedGradleUserHome(Paths.get(sysPropValue.trim()), GRADLE_USER_HOME_SYS_PROP, diagnostics)
            }
            diagnostics += "Server system property $GRADLE_USER_HOME_SYS_PROP is blank; skipping."
        }

        val envValue = serverEnv(GRADLE_USER_HOME_ENV)
        if (envValue != null) {
            if (envValue.isNotBlank()) {
                return ResolvedGradleUserHome(Paths.get(envValue.trim()), GRADLE_USER_HOME_ENV, diagnostics)
            }
            diagnostics += "Server environment variable $GRADLE_USER_HOME_ENV is blank; skipping."
        }

        return ResolvedGradleUserHome(defaultUserHome(), "default", diagnostics)
    }
}

/**
 * The terminal daemon JVM selection decision. Each decision carries the diagnostics the caller must
 * emit; helper functions never log directly.
 */
sealed interface DaemonJvmDecision {
    val diagnostics: List<String>

    /** Use `Launcher.setJavaHome(home)`; the source is either explicit input or environment `JAVA_HOME`. */
    data class UseJavaHome(val home: File, val origin: Origin, override val diagnostics: List<String> = emptyList()) : DaemonJvmDecision {
        enum class Origin { EXPLICIT, ENVIRONMENT }
    }

    /** Omit `setJavaHome`; project daemon JVM settings were detected (or a file failed closed). */
    data class DeferToGradleSettings(
        val detection: DaemonJvmSettingsDetection,
        override val diagnostics: List<String> = emptyList()
    ) : DaemonJvmDecision

    /** Omit `setJavaHome`; no explicit value, no daemon settings, and no usable environment value. */
    data object ToolingApiDefault : DaemonJvmDecision {
        override val diagnostics: List<String> get() = emptyList()
    }

    /** Explicit `javaHome` is invalid: warn, omit `setJavaHome`, and never consult lower-priority sources. */
    data class InvalidExplicit(val value: String, override val diagnostics: List<String>) : DaemonJvmDecision

    /** Environment `JAVA_HOME` is invalid: warn and omit `setJavaHome`. */
    data class InvalidEnvHome(val value: String, override val diagnostics: List<String>) : DaemonJvmDecision
}

/**
 * Decides the daemon JVM source with this terminal precedence:
 *  1. a non-null explicit `javaHome` is terminal: a valid directory becomes [DaemonJvmDecision.UseJavaHome]
 *     with origin EXPLICIT; an invalid path becomes [DaemonJvmDecision.InvalidExplicit] (warn, omit, and no
 *     settings detection or environment fallback);
 *  2. with no explicit value, detect project daemon JVM settings; any effective or fail-closed source
 *     becomes [DaemonJvmDecision.DeferToGradleSettings];
 *  3. with no detected settings, a valid environment `JAVA_HOME` becomes [DaemonJvmDecision.UseJavaHome]
 *     with origin ENVIRONMENT; an invalid value becomes [DaemonJvmDecision.InvalidEnvHome];
 *  4. with no usable source, [DaemonJvmDecision.ToolingApiDefault].
 *
 * The selector is stateless; file detection runs on every call (no caching). On Windows the
 * environment `JAVA_HOME` lookup matches keys case-insensitively ([envLookupCaseInsensitive]).
 */
class DaemonJvmSelector(
    private val detector: DaemonJvmSettingsDetector = DaemonJvmSettingsDetector(),
    private val userHomeResolver: EffectiveGradleUserHomeResolver = EffectiveGradleUserHomeResolver(),
    private val envLookupCaseInsensitive: Boolean = OS.isWindows
) {
    fun decide(args: GradleInvocationArguments, env: Map<String, String>, projectRoot: Path): DaemonJvmDecision {
        val explicit = args.javaHome
        if (explicit != null) {
            val file = File(explicit)
            return if (file.exists() && file.isDirectory) {
                DaemonJvmDecision.UseJavaHome(file, DaemonJvmDecision.UseJavaHome.Origin.EXPLICIT)
            } else {
                DaemonJvmDecision.InvalidExplicit(
                    explicit,
                    listOf(
                        "Specified javaHome does not exist or is not a directory: $explicit. " +
                            "No other JVM source will be promoted; Gradle's own daemon JVM resolution applies."
                    )
                )
            }
        }

        val resolvedUserHome = userHomeResolver.resolve()
        val detection = detector.detect(projectRoot, resolvedUserHome.path)
        if (detection.settingsPresent) {
            return DaemonJvmDecision.DeferToGradleSettings(detection, resolvedUserHome.diagnostics + detection.diagnostics)
        }

        val envJavaHome = findEnvValue(env, JAVA_HOME_ENV)
        if (envJavaHome != null) {
            val file = File(envJavaHome)
            return if (file.exists() && file.isDirectory) {
                DaemonJvmDecision.UseJavaHome(file, DaemonJvmDecision.UseJavaHome.Origin.ENVIRONMENT, resolvedUserHome.diagnostics)
            } else {
                DaemonJvmDecision.InvalidEnvHome(
                    envJavaHome,
                    resolvedUserHome.diagnostics +
                        listOf("Environment (JAVA_HOME) javaHome does not exist or is not a directory: $envJavaHome")
                )
            }
        }

        return DaemonJvmDecision.ToolingApiDefault
    }

    private fun findEnvValue(env: Map<String, String>, key: String): String? =
        if (envLookupCaseInsensitive) {
            env.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        } else {
            env[key]
        }
}

/** The outcome of launcher Java-home configuration: the diagnostics to emit plus the optional
 * `org.gradle.java.home` enforcement argument to include in the build's `withArguments` list. */
internal data class JavaHomeConfiguration(
    val diagnostics: List<String>,
    val javaHomeArgument: String?
)

/**
 * Thin mapper from a [DaemonJvmDecision] to `Launcher` configuration. Emits every diagnostic at
 * WARN, logs the chosen action at INFO, and returns the emitted diagnostics plus the enforcement
 * argument for testability.
 *
 * Enforcement: an EXPLICIT [DaemonJvmDecision.UseJavaHome] result also yields the raw
 * `-Dorg.gradle.java.home=<home>` build argument ([ORG_GRADLE_JAVA_HOME_ARG]). Gradle converts
 * `org.gradle.java.home` properties into daemon JVM criteria before applying the Tooling API
 * java-home request and eagerly validates them (invalid values abort before daemon selection); the
 * raw `-D` argument overrides the merged property value before that validation, and `setJavaHome`
 * is applied last, so the pair is safe. This uses the same client-side `-D` launcher-argument
 * channel as the in-repo `TEST_DAEMON_IDLE_TIMEOUT_ARG` precedent (`TestGradleProvider.kt`):
 * `ProviderConnection.initParams` feeds initial `-D` arguments into `DaemonBuildOptions` AFTER the
 * user-home gradle.properties conversion.
 *
 * Every other decision — INCLUDING [DaemonJvmDecision.UseJavaHome] with origin ENVIRONMENT — yields
 * a null argument: enforcement is EXPLICIT-only by design, so environment promotion never overrides
 * a project's daemon JVM settings.
 */
internal fun configureJavaHome(
    launcher: ConfigurableLauncher<*>,
    args: GradleInvocationArguments,
    env: Map<String, String>,
    projectRoot: Path,
    selector: DaemonJvmSelector,
    logger: Logger = LoggerFactory.getLogger("dev.rnett.gradle.mcp.gradle.build.DaemonJvmSelection")
): JavaHomeConfiguration {
    val decision = selector.decide(args, env, projectRoot)
    decision.diagnostics.forEach { logger.warn(it) }
    var javaHomeArgument: String? = null
    when (decision) {
        is DaemonJvmDecision.UseJavaHome -> {
            logger.info("Using {} Java home for the Gradle daemon: {}", decision.origin, decision.home)
            launcher.setJavaHome(decision.home)
            if (decision.origin == DaemonJvmDecision.UseJavaHome.Origin.EXPLICIT) {
                javaHomeArgument = ORG_GRADLE_JAVA_HOME_ARG + decision.home.path
            }
        }
        is DaemonJvmDecision.DeferToGradleSettings -> {
            val sources = decision.detection.sources.joinToString("; ") { it.describe() }
            logger.info("Deferring daemon JVM selection to Gradle settings; detected source(s): {}", sources)
        }
        is DaemonJvmDecision.ToolingApiDefault -> Unit
        is DaemonJvmDecision.InvalidExplicit -> Unit
        is DaemonJvmDecision.InvalidEnvHome -> Unit
    }
    return JavaHomeConfiguration(decision.diagnostics, javaHomeArgument)
}

private fun DaemonJvmSettingSource.describe(): String = when (this) {
    is DaemonJvmSettingSource.DaemonJvmCriteria -> "daemon JVM criteria (toolchainVersion=$toolchainVersion) in $file"
    is DaemonJvmSettingSource.OrgGradleJavaHome -> "org.gradle.java.home in ${scope.name.lowercase()} gradle.properties ($file)"
    is DaemonJvmSettingSource.Unparseable -> "unreadable/unparseable settings file $file ($cause)"
    is DaemonJvmSettingSource.InvalidValue -> "invalid value for $key (blank) in $file"
}
