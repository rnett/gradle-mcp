package dev.rnett.gradle.mcp.gradle.build

import dev.rnett.gradle.mcp.gradle.GradleInvocationArguments
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.tooling.ConfigurableLauncher
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Tier-1 source-selection proof for daemon JVM selection: detector cases D1-D8, resolver
 * precedence D9-D13, selector terminal-precedence D14-D18, and launcher-mapping interaction
 * L1-L6. All filesystem inputs are real temp dirs; process state is injected via lambdas.
 */
class DaemonJvmSelectionTest {

    @TempDir
    lateinit var tempDir: Path

    private fun projectDir(name: String = "project") = tempDir.resolve(name).createDirectories()

    private fun homeDir(name: String = "home") = tempDir.resolve(name).createDirectories()

    private fun validJdkDir(name: String = "jdk") = tempDir.resolve(name).createDirectories()

    private fun resolver(defaultHome: Path): EffectiveGradleUserHomeResolver = EffectiveGradleUserHomeResolver(
        serverSysProp = { null },
        serverEnv = { null },
        defaultUserHome = { defaultHome }
    )

    private class StubDetector(private val result: DaemonJvmSettingsDetection) : DaemonJvmSettingsDetector() {
        override fun detect(projectRoot: Path, gradleUserHome: Path): DaemonJvmSettingsDetection = result
    }

    private class ThrowingDetector : DaemonJvmSettingsDetector() {
        override fun detect(projectRoot: Path, gradleUserHome: Path): DaemonJvmSettingsDetection =
            error("Detector must not be consulted")
    }

    // ---------- D1-D8: DaemonJvmSettingsDetector ----------

    @Test
    fun `D1 S1 daemon JVM criteria with effective toolchainVersion is present`() {
        val projectRoot = projectDir()
        val criteriaFile = projectRoot.resolve("gradle").resolve("gradle-daemon-jvm.properties")
        criteriaFile.parent.createDirectories()
        criteriaFile.writeText("toolchainVersion = 21 \n")

        val detection = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())

        assert(detection.settingsPresent)
        assert(detection.sources.single() == DaemonJvmSettingSource.DaemonJvmCriteria(criteriaFile, "21"))
        assert(detection.diagnostics.isEmpty())
    }

    @Test
    fun `D2 S1 without toolchainVersion is absent but a blank toolchainVersion fails closed`() {
        val projectRoot = projectDir()
        val criteriaFile = projectRoot.resolve("gradle").resolve("gradle-daemon-jvm.properties")
        criteriaFile.parent.createDirectories()

        // A criteria file without a toolchainVersion key (e.g. only a vendor) is not effective.
        criteriaFile.writeText("toolchainVendor=temurin\n")
        val noVersion = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())
        assert(!noVersion.settingsPresent)
        assert(noVersion.diagnostics.isEmpty())

        // Probe V2: Gradle rejects a blank toolchainVersion, so detection fails closed (present)
        // with an InvalidValue source and a diagnostic.
        criteriaFile.writeText("toolchainVersion=   \n")
        val blankVersion = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())
        assert(blankVersion.settingsPresent)
        val invalid = blankVersion.sources.single() as DaemonJvmSettingSource.InvalidValue
        assert(invalid.key == "toolchainVersion")
        assert(blankVersion.diagnostics.size == 1)
        assert("blank toolchainVersion" in blankVersion.diagnostics.single())
    }

    @Test
    fun `D3 missing settings files are absent without diagnostics`() {
        val detection = DaemonJvmSettingsDetector().detect(projectDir(), homeDir())
        assert(!detection.settingsPresent)
        assert(detection.sources.isEmpty())
        assert(detection.diagnostics.isEmpty())
    }

    @Test
    fun `D4 S2 user-home org gradle java home is present with USER_HOME scope`() {
        val projectRoot = projectDir()
        val home = homeDir()
        val userHomeProps = home.resolve("gradle.properties")
        userHomeProps.writeText("org.gradle.java.home=/opt/jdk\n")

        val detection = DaemonJvmSettingsDetector().detect(projectRoot, home)

        assert(detection.settingsPresent)
        val source = detection.sources.single() as DaemonJvmSettingSource.OrgGradleJavaHome
        assert(source.scope == DaemonJvmSettingSource.OrgGradleJavaHome.Scope.USER_HOME)
        assert(source.file == userHomeProps)
    }

    @Test
    fun `D5 S3 project gradle properties is present with PROJECT scope`() {
        val projectRoot = projectDir()
        val projectProps = projectRoot.resolve("gradle.properties")
        projectProps.writeText("org.gradle.java.home=/opt/jdk\n")

        val detection = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())

        assert(detection.settingsPresent)
        val source = detection.sources.single() as DaemonJvmSettingSource.OrgGradleJavaHome
        assert(source.scope == DaemonJvmSettingSource.OrgGradleJavaHome.Scope.PROJECT)
        assert(source.file == projectProps)
    }

    @Test
    fun `D5b blank org gradle java home fails closed`() {
        val projectRoot = projectDir()
        val home = homeDir()
        home.resolve("gradle.properties").writeText("org.gradle.java.home=   \n")

        // Probe V2: Gradle rejects a blank org.gradle.java.home, so detection fails closed.
        val detection = DaemonJvmSettingsDetector().detect(projectRoot, home)
        assert(detection.settingsPresent)
        val invalid = detection.sources.single() as DaemonJvmSettingSource.InvalidValue
        assert(invalid.key == "org.gradle.java.home")
        assert(detection.diagnostics.any { "blank org.gradle.java.home" in it })
    }

    @Test
    fun `D6 fail closed on unreadable or unparseable settings files`() {
        val projectRoot = projectDir()

        // Parse failure: invalid unicode escape is rejected by java.util.Properties.load.
        val criteriaFile = projectRoot.resolve("gradle").resolve("gradle-daemon-jvm.properties")
        criteriaFile.parent.createDirectories()
        criteriaFile.writeText("toolchainVersion=21\\uZZZZ\n")
        val parseFailure = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())
        assert(parseFailure.settingsPresent)
        val unparseable = parseFailure.sources.single() as DaemonJvmSettingSource.Unparseable
        assert(unparseable.file == criteriaFile)
        assert(parseFailure.diagnostics.any { "could not be read or parsed" in it })

        // Read failure: an existing directory at the gradle.properties path cannot be read as a file.
        val home2 = homeDir("home2")
        Files.createDirectory(home2.resolve("gradle.properties"))
        val readFailure = DaemonJvmSettingsDetector().detect(projectDir("project2"), home2)
        assert(readFailure.settingsPresent)
        val readUnparseable = readFailure.sources.single() as DaemonJvmSettingSource.Unparseable
        assert(readUnparseable.file == home2.resolve("gradle.properties"))
        assert(readFailure.diagnostics.any { "could not be read or parsed" in it })
    }

    @Test
    fun `D7 values are normalized with properties semantics and trimming`() {
        val projectRoot = projectDir()
        val criteriaFile = projectRoot.resolve("gradle").resolve("gradle-daemon-jvm.properties")
        criteriaFile.parent.createDirectories()
        criteriaFile.writeText("toolchainVersion =  21  \n")
        val detection1 = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())
        val criteria = detection1.sources.single() as DaemonJvmSettingSource.DaemonJvmCriteria
        assert(criteria.toolchainVersion == "21")

        // Properties escapes are honored and the resolved value is trimmed before use.
        projectRoot.resolve("gradle.properties").writeText("org.gradle.java.home = C\\:\\\\opt\\\\jdk21 \n")
        val detection2 = DaemonJvmSettingsDetector().detect(projectRoot, homeDir())
        assert(
            detection2.sources.any {
                it is DaemonJvmSettingSource.OrgGradleJavaHome &&
                    it.scope == DaemonJvmSettingSource.OrgGradleJavaHome.Scope.PROJECT
            }
        )
    }

    @Test
    fun `D8 distribution-level gradle properties is not consulted`() {
        val projectRoot = projectDir()
        // A distribution-like directory passed as the user home: only its top-level gradle.properties
        // is the user-home settings file; anything nested in the distribution layout is not read.
        val distLikeHome = tempDir.resolve("gradle-9.4.1").createDirectories()
        distLikeHome.resolve("lib").createDirectories()
        distLikeHome.resolve("lib").resolve("gradle.properties")
            .writeText("org.gradle.java.home=/not/consulted\n")

        val detection1 = DaemonJvmSettingsDetector().detect(projectRoot, distLikeHome)
        assert(!detection1.settingsPresent)
        assert(detection1.diagnostics.isEmpty())

        // The top-level gradle.properties of the same directory IS the user-home file and is read.
        distLikeHome.resolve("gradle.properties").writeText("org.gradle.java.home=/opt/jdk\n")
        val detection2 = DaemonJvmSettingsDetector().detect(projectRoot, distLikeHome)
        assert(detection2.settingsPresent)
        val source = detection2.sources.single() as DaemonJvmSettingSource.OrgGradleJavaHome
        assert(source.scope == DaemonJvmSettingSource.OrgGradleJavaHome.Scope.USER_HOME)
    }

    // ---------- D9-D13: EffectiveGradleUserHomeResolver ----------

    @Test
    fun `D9 resolver prefers the server system property channel`() {
        val sysPropHome = tempDir.resolve("sys-prop-home")
        val envHome = tempDir.resolve("env-home")
        val resolver = EffectiveGradleUserHomeResolver(
            serverSysProp = { if (it == GRADLE_USER_HOME_SYS_PROP) sysPropHome.toString() else null },
            serverEnv = { envHome.toString() },
            defaultUserHome = { tempDir.resolve("default-home") }
        )

        val resolved = resolver.resolve()

        assert(resolved.path == sysPropHome)
        assert(resolved.channel == GRADLE_USER_HOME_SYS_PROP)
        assert(resolved.diagnostics.isEmpty())
    }

    @Test
    fun `D10 resolver falls to the server environment channel when the system property is absent`() {
        val envHome = tempDir.resolve("env-home")
        val resolver = EffectiveGradleUserHomeResolver(
            serverSysProp = { null },
            serverEnv = { if (it == GRADLE_USER_HOME_ENV) envHome.toString() else null },
            defaultUserHome = { tempDir.resolve("default-home") }
        )

        val resolved = resolver.resolve()

        assert(resolved.path == envHome)
        assert(resolved.channel == GRADLE_USER_HOME_ENV)
        assert(resolved.diagnostics.isEmpty())
    }

    @Test
    fun `D11 resolver falls back to the default user home`() {
        val defaultHome = tempDir.resolve("default-home")
        val resolver = EffectiveGradleUserHomeResolver(
            serverSysProp = { null },
            serverEnv = { null },
            defaultUserHome = { defaultHome }
        )

        val resolved = resolver.resolve()

        assert(resolved.path == defaultHome)
        assert(resolved.channel == "default")
        assert(resolved.diagnostics.isEmpty())
    }

    @Test
    fun `D12 blank channels are skipped with a diagnostic`() {
        val envHome = tempDir.resolve("env-home")
        val resolver = EffectiveGradleUserHomeResolver(
            serverSysProp = { "   " },
            serverEnv = { envHome.toString() },
            defaultUserHome = { tempDir.resolve("default-home") }
        )

        val resolved = resolver.resolve()

        assert(resolved.path == envHome)
        assert(resolved.channel == GRADLE_USER_HOME_ENV)
        assert(resolved.diagnostics.size == 1)
        assert(GRADLE_USER_HOME_SYS_PROP in resolved.diagnostics.single())
        assert("blank" in resolved.diagnostics.single())
    }

    @Test
    fun `D13 server environment lookup uses the canonical GRADLE_USER_HOME key`() {
        val envHome = tempDir.resolve("env-home")
        val seenKeys = mutableListOf<String>()
        val resolver = EffectiveGradleUserHomeResolver(
            serverSysProp = { null },
            serverEnv = { key -> seenKeys += key; envHome.toString() },
            defaultUserHome = { tempDir.resolve("default-home") }
        )

        val resolved = resolver.resolve()

        // On Windows System.getenv is case-insensitive; the resolver must always query the canonical key.
        assert(seenKeys == listOf(GRADLE_USER_HOME_ENV))
        assert(resolved.channel == GRADLE_USER_HOME_ENV)
    }

    // ---------- D14-D18: DaemonJvmSelector ----------

    @Test
    fun `D14 explicit valid javaHome is terminal and detector is never consulted`() {
        val jdk = validJdkDir()
        val selector = DaemonJvmSelector(detector = ThrowingDetector(), userHomeResolver = resolver(homeDir()))

        val decision = selector.decide(
            GradleInvocationArguments(javaHome = jdk.toString()),
            mapOf("JAVA_HOME" to tempDir.resolve("other").toString()),
            projectDir()
        )

        assert(decision is DaemonJvmDecision.UseJavaHome)
        val use = decision as DaemonJvmDecision.UseJavaHome
        assert(use.origin == DaemonJvmDecision.UseJavaHome.Origin.EXPLICIT)
        assert(use.home == jdk.toFile())
        assert(use.diagnostics.isEmpty())
    }

    @Test
    fun `D15 explicit invalid javaHome is terminal with no fallback`() {
        val missing = tempDir.resolve("missing-jdk").toString()
        val jdk = validJdkDir()
        val selector = DaemonJvmSelector(detector = ThrowingDetector(), userHomeResolver = resolver(homeDir()))

        val decision = selector.decide(
            GradleInvocationArguments(javaHome = missing),
            mapOf("JAVA_HOME" to jdk.toString()),
            projectDir()
        )

        assert(decision is DaemonJvmDecision.InvalidExplicit)
        val invalid = decision as DaemonJvmDecision.InvalidExplicit
        assert(invalid.value == missing)
        assert(invalid.diagnostics.single().contains("Specified javaHome"))
    }

    @Test
    fun `D16 detected settings defer to Gradle even when environment is set`() {
        val jdk = validJdkDir()
        val detection = DaemonJvmSettingsDetection(
            settingsPresent = true,
            sources = listOf(DaemonJvmSettingSource.OrgGradleJavaHome(tempDir.resolve("p").resolve("gradle.properties"), DaemonJvmSettingSource.OrgGradleJavaHome.Scope.PROJECT)),
            diagnostics = emptyList()
        )
        val selector = DaemonJvmSelector(detector = StubDetector(detection), userHomeResolver = resolver(homeDir()))

        val decision = selector.decide(
            GradleInvocationArguments(),
            mapOf("JAVA_HOME" to jdk.toString()),
            projectDir()
        )

        assert(decision is DaemonJvmDecision.DeferToGradleSettings)
        assert((decision as DaemonJvmDecision.DeferToGradleSettings).detection.settingsPresent)
    }

    @Test
    fun `D17 environment fallback is preserved when no settings are detected`() {
        val jdk = validJdkDir()
        val selector = DaemonJvmSelector(detector = StubDetector(DaemonJvmSettingsDetection.ABSENT), userHomeResolver = resolver(homeDir()))

        val decision = selector.decide(GradleInvocationArguments(), mapOf("JAVA_HOME" to jdk.toString()), projectDir())

        assert(decision is DaemonJvmDecision.UseJavaHome)
        val use = decision as DaemonJvmDecision.UseJavaHome
        assert(use.origin == DaemonJvmDecision.UseJavaHome.Origin.ENVIRONMENT)
        assert(use.home == jdk.toFile())
    }

    @Test
    fun `D18 invalid or absent environment yields InvalidEnvHome or ToolingApiDefault`() {
        val missing = tempDir.resolve("missing-env-jdk").toString()
        val selector = DaemonJvmSelector(detector = StubDetector(DaemonJvmSettingsDetection.ABSENT), userHomeResolver = resolver(homeDir()))

        val invalidEnv = selector.decide(GradleInvocationArguments(), mapOf("JAVA_HOME" to missing), projectDir())
        assert(invalidEnv is DaemonJvmDecision.InvalidEnvHome)
        assert((invalidEnv as DaemonJvmDecision.InvalidEnvHome).value == missing)
        assert(invalidEnv.diagnostics.single().contains("Environment (JAVA_HOME)"))

        val absentEnv = selector.decide(GradleInvocationArguments(), emptyMap(), projectDir())
        assert(absentEnv is DaemonJvmDecision.ToolingApiDefault)
    }

    @Test
    fun `selector finds JAVA_HOME case-insensitively when Windows lookup is enabled`() {
        val jdk = validJdkDir()
        val windowsSelector = DaemonJvmSelector(
            detector = StubDetector(DaemonJvmSettingsDetection.ABSENT),
            userHomeResolver = resolver(homeDir()),
            envLookupCaseInsensitive = true
        )
        val mixedCase = windowsSelector.decide(GradleInvocationArguments(), mapOf("java_home" to jdk.toString()), projectDir())
        assert(mixedCase is DaemonJvmDecision.UseJavaHome)
        assert((mixedCase as DaemonJvmDecision.UseJavaHome).origin == DaemonJvmDecision.UseJavaHome.Origin.ENVIRONMENT)

        val strictSelector = DaemonJvmSelector(
            detector = StubDetector(DaemonJvmSettingsDetection.ABSENT),
            userHomeResolver = resolver(homeDir()),
            envLookupCaseInsensitive = false
        )
        val strict = strictSelector.decide(GradleInvocationArguments(), mapOf("java_home" to jdk.toString()), projectDir())
        assert(strict is DaemonJvmDecision.ToolingApiDefault)
    }

    // ---------- L1-L6: configureJavaHome launcher mapping ----------

    private fun mockLauncher() = mockk<ConfigurableLauncher<*>>()

    @Test
    fun `L1 UseJavaHome EXPLICIT issues setJavaHome exactly once and yields the enforcement argument`() {
        val launcher = mockLauncher()
        every { launcher.setJavaHome(any()) } returns launcher
        val jdk = validJdkDir()
        val selector = DaemonJvmSelector(detector = ThrowingDetector(), userHomeResolver = resolver(homeDir()))

        val result = configureJavaHome(
            launcher, GradleInvocationArguments(javaHome = jdk.toString()),
            emptyMap(), projectDir(), selector
        )

        verify(exactly = 1) { launcher.setJavaHome(jdk.toFile()) }
        assert(result.diagnostics.isEmpty())
        // Enforcement is EXPLICIT-only: the explicit home is also passed as the raw
        // `-Dorg.gradle.java.home=<home>` build argument so it wins over lower-priority
        // `gradle.properties` values, including invalid ones Gradle validates before daemon selection.
        assert(result.javaHomeArgument == ORG_GRADLE_JAVA_HOME_ARG + jdk.toString())
    }

    @Test
    fun `L2 UseJavaHome ENVIRONMENT issues setJavaHome exactly once without the enforcement argument`() {
        val launcher = mockLauncher()
        every { launcher.setJavaHome(any()) } returns launcher
        val jdk = validJdkDir()
        val selector = DaemonJvmSelector(detector = StubDetector(DaemonJvmSettingsDetection.ABSENT), userHomeResolver = resolver(homeDir()))

        val result = configureJavaHome(
            launcher, GradleInvocationArguments(),
            mapOf("JAVA_HOME" to jdk.toString()), projectDir(), selector
        )

        verify(exactly = 1) { launcher.setJavaHome(jdk.toFile()) }
        assert(result.diagnostics.isEmpty())
        // Enforcement is EXPLICIT-only: environment promotion never overrides a project's daemon
        // JVM settings, so no `-Dorg.gradle.java.home` argument is emitted.
        assert(result.javaHomeArgument == null)
    }

    @Test
    fun `L3 DeferToGradleSettings never calls setJavaHome and surfaces fail-closed diagnostics`() {
        val launcher = mockLauncher()
        // A real malformed criteria file makes the detector fail closed, proving the defer action
        // for the Unparseable source as well.
        val projectRoot = projectDir()
        val criteriaFile = projectRoot.resolve("gradle").resolve("gradle-daemon-jvm.properties")
        criteriaFile.parent.createDirectories()
        criteriaFile.writeText("toolchainVersion=21\\uZZZZ\n")
        val selector = DaemonJvmSelector(detector = DaemonJvmSettingsDetector(), userHomeResolver = resolver(homeDir()))

        val result = configureJavaHome(
            launcher, GradleInvocationArguments(),
            mapOf("JAVA_HOME" to validJdkDir().toString()), projectRoot, selector
        )

        verify(exactly = 0) { launcher.setJavaHome(any()) }
        assert(result.diagnostics.isNotEmpty())
        assert(result.diagnostics.any { "could not be read or parsed" in it })
        assert(result.javaHomeArgument == null)
    }

    @Test
    fun `L4 ToolingApiDefault never calls setJavaHome`() {
        val launcher = mockLauncher()
        val selector = DaemonJvmSelector(detector = StubDetector(DaemonJvmSettingsDetection.ABSENT), userHomeResolver = resolver(homeDir()))

        val result = configureJavaHome(launcher, GradleInvocationArguments(), emptyMap(), projectDir(), selector)

        verify(exactly = 0) { launcher.setJavaHome(any()) }
        assert(result.javaHomeArgument == null)
    }

    @Test
    fun `L5 InvalidExplicit never calls setJavaHome and returns the WARN diagnostic`() {
        val launcher = mockLauncher()
        val missing = tempDir.resolve("missing-jdk").toString()
        val selector = DaemonJvmSelector(detector = ThrowingDetector(), userHomeResolver = resolver(homeDir()))

        val result = configureJavaHome(
            launcher, GradleInvocationArguments(javaHome = missing),
            mapOf("JAVA_HOME" to validJdkDir().toString()), projectDir(), selector
        )

        verify(exactly = 0) { launcher.setJavaHome(any()) }
        assert(result.diagnostics.size == 1)
        assert(result.diagnostics.single().contains("Specified javaHome"))
        assert(result.javaHomeArgument == null)
    }

    @Test
    fun `L6 InvalidEnvHome never calls setJavaHome and returns the WARN diagnostic`() {
        val launcher = mockLauncher()
        val missing = tempDir.resolve("missing-env-jdk").toString()
        val selector = DaemonJvmSelector(detector = StubDetector(DaemonJvmSettingsDetection.ABSENT), userHomeResolver = resolver(homeDir()))

        val result = configureJavaHome(
            launcher, GradleInvocationArguments(),
            mapOf("JAVA_HOME" to missing), projectDir(), selector
        )

        verify(exactly = 0) { launcher.setJavaHome(any()) }
        assert(result.diagnostics.size == 1)
        assert(result.diagnostics.single().contains("Environment (JAVA_HOME)"))
        assert(result.javaHomeArgument == null)
    }
}
