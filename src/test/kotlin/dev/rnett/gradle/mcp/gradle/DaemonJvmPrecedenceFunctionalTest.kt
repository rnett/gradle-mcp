package dev.rnett.gradle.mcp.gradle

import dev.rnett.gradle.mcp.fixtures.SharedTestInfrastructure
import dev.rnett.gradle.mcp.fixtures.gradle.GradleProjectFixture
import dev.rnett.gradle.mcp.fixtures.gradle.TestGradleUserHome
import dev.rnett.gradle.mcp.fixtures.gradle.printJavaHomeTask
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProject
import dev.rnett.gradle.mcp.fixtures.gradle.testGradleProvider
import dev.rnett.gradle.mcp.gradle.build.BuildOutcome
import dev.rnett.gradle.mcp.gradle.build.FinishedBuild
import dev.rnett.gradle.mcp.gradle.build.failuresIfFailed
import dev.rnett.gradle.mcp.utils.EnvProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.seconds

/**
 * Tier-2 end-to-end parity checks for daemon JVM selection precedence (T1-T6 + T1b). Every nested
 * build runs under a controlled Gradle user home (class-scoped [TestGradleUserHome]) with the
 * connector pinned to that home via `testGradleProvider`, `withTestGradleDefaults(pinJavaHome =
 * false)`, and env `JAVA_HOME` supplied through a mocked [EnvProvider]. The `printJavaHome` probe
 * task prints the daemon JVM's `java.home`; assertions compare canonical paths.
 *
 * Branch *selection* is proven by the Tier-1 tests ([dev.rnett.gradle.mcp.gradle.build.DaemonJvmSelectionTest]);
 * these tests prove Gradle behaves consistently with the chosen action (parity), and that wrongful
 * `setJavaHome` calls fail loudly against the non-JDK sentinel directories.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DaemonJvmPrecedenceFunctionalTest {

    private lateinit var gradleUserHome: TestGradleUserHome

    /** The test-worker JDK home — the only real JDK guaranteed on every machine. */
    private lateinit var j: Path

    @BeforeAll
    fun setupAll() {
        gradleUserHome = TestGradleUserHome.create()
        j = Path.of(System.getProperty("java.home")).toRealPath()
    }

    @AfterAll
    fun cleanupAll() {
        gradleUserHome.close()
    }

    @BeforeEach
    fun resetHome() {
        gradleUserHome.resetDaemonSettings()
    }

    /** A provider whose nested builds observe exactly [env] (no inherited machine state). */
    private fun providerWith(env: Map<String, String>) = testGradleProvider(
        gradleUserHome = gradleUserHome.path,
        envProvider = object : EnvProvider {
            override fun getShellEnvironment(): Map<String, String> = env
            override fun getInheritedEnvironment(): Map<String, String> = env
        },
        pinJavaHome = false
    )

    /**
     * A valid directory that is NOT a JDK (marker file, no `release`/`bin/java`). It passes the
     * `exists() && isDirectory` validity check, so a wrongful `setJavaHome(Fx)` is actually
     * attempted and daemon startup fails loudly.
     */
    private fun newNonJdkSentinelDir(name: String): Path {
        val dir = SharedTestInfrastructure.sharedWorkingDir.resolve("daemon-jvm-$name").createDirectories()
        Files.writeString(dir.resolve("not-a-jdk-marker.txt"), "not a JDK")
        return dir.toRealPath()
    }

    private fun propertiesValue(v: String): String = v.replace("\\", "\\\\")

    private fun probeJavaHome(result: FinishedBuild): Path {
        val line = result.consoleOutput.lineSequence().firstOrNull { it.contains(DAEMON_JVM_PROBE_MARKER) }
            ?: error("No daemon-jvm-probe marker in build output:\n${result.consoleOutput}")
        return Path.of(line.substringAfter("$DAEMON_JVM_PROBE_MARKER java.home=").trim()).toRealPath()
    }

    private suspend fun runProbe(p: GradleProvider, project: GradleProjectFixture, extraArgs: GradleInvocationArguments = GradleInvocationArguments()): FinishedBuild {
        val args = extraArgs.copy(additionalArguments = listOf("printJavaHome"))
        val result = p.runBuild(GradleProjectRoot(project.pathString()), args).awaitFinished()
        return result
    }

    // ---------- T1-T6 ----------

    @Test
    fun `T1 explicit javaHome wins over settings and environment`() = runTest(timeout = 300.seconds) {
        // Discrimination chain: F1 (project org.gradle.java.home) is a non-JDK sentinel directory.
        // Gradle eagerly validates java.home values and rejects non-JDK directories before daemon
        // selection (probe V2 mechanics), so if the settings channel won, the build would fail.
        // F2 (env JAVA_HOME) is another non-JDK sentinel; a wrongful env promotion would start the
        // daemon against it and fail loudly. Only the explicit JDK J yields Success + probe == J:
        // the explicit home wins even over an INVALID lower-priority setting (enforced via the
        // `-Dorg.gradle.java.home=<J>` build argument, which overrides the merged property value
        // before Gradle's validation).
        val f1 = newNonJdkSentinelDir("t1-f1")
        val f2 = newNonJdkSentinelDir("t1-f2")
        testGradleProject {
            printJavaHomeTask()
            file("gradle.properties", "org.gradle.java.home=${propertiesValue(f1.toString())}\n")
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to f2.toString())).use { p ->
                val result = runProbe(
                    p, project,
                    GradleInvocationArguments(javaHome = j.toString(), envSource = EnvSource.INHERIT)
                )

                assert(result.outcome is BuildOutcome.Success) {
                    "build failed: ${result.consoleOutput}"
                }
                assert(probeJavaHome(result) == j)
            }
        }
    }

    @Test
    fun `T1b explicit javaHome wins over valid daemon criteria`() = runTest(timeout = 300.seconds) {
        // Discrimination chain: the project declares daemon JVM criteria with an unmatchable
        // toolchainVersion (99 — no installed JVM satisfies it), so if criteria won (no enforcement)
        // the build fails on daemon selection. Env JAVA_HOME is a non-JDK sentinel directory; a
        // wrongful env promotion would start the daemon against it and fail loudly. Only the
        // explicit JDK J yields Success + probe == J: the enforcement argument beats valid daemon
        // criteria (org.gradle.java.home criteria outrank toolchainVersion criteria).
        val f2 = newNonJdkSentinelDir("t1b-f2")
        testGradleProject {
            printJavaHomeTask()
            file("gradle/gradle-daemon-jvm.properties", "toolchainVersion=99\n")
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to f2.toString())).use { p ->
                val result = runProbe(
                    p, project,
                    GradleInvocationArguments(javaHome = j.toString(), envSource = EnvSource.INHERIT)
                )

                assert(result.outcome is BuildOutcome.Success) {
                    "build failed: ${result.consoleOutput}"
                }
                assert(probeJavaHome(result) == j)
            }
        }
    }

    @Test
    fun `T2 user-home org gradle java home wins over project settings`() = runTest(timeout = 300.seconds) {
        val f1 = newNonJdkSentinelDir("t2-f1")
        val f2 = newNonJdkSentinelDir("t2-f2")
        gradleUserHome.writeGradleProperties("org.gradle.java.home=${propertiesValue(j.toString())}\n")
        testGradleProject {
            printJavaHomeTask()
            file("gradle.properties", "org.gradle.java.home=${propertiesValue(f1.toString())}\n")
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to f2.toString())).use { p ->
                val result = runProbe(p, project, GradleInvocationArguments(envSource = EnvSource.INHERIT))

                assert(result.outcome is BuildOutcome.Success) { "build failed: ${result.consoleOutput}" }
                assert(probeJavaHome(result) == j)
            }
        }
    }

    @Test
    fun `T3 daemon JVM toolchain criteria defers to Gradle`() = runTest(timeout = 300.seconds) {
        val f2 = newNonJdkSentinelDir("t3-f2")
        val jMajor = System.getProperty("java.specification.version").substringBefore('.')
        testGradleProject {
            printJavaHomeTask()
            file("gradle/gradle-daemon-jvm.properties", "toolchainVersion=$jMajor\n")
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to f2.toString())).use { p ->
                val result = runProbe(p, project, GradleInvocationArguments(envSource = EnvSource.INHERIT))

                assert(result.outcome is BuildOutcome.Success) { "build failed: ${result.consoleOutput}" }
                val probe = probeJavaHome(result)
                assert(probe != f2) { "env promotion must not win over daemon criteria" }
                val release = probe.resolve("release")
                assert(Files.isRegularFile(release)) { "probe is not a real JDK: $probe" }
                val releaseVersion = Files.readAllLines(release).firstOrNull { it.startsWith("JAVA_VERSION=") }
                    ?: error("no JAVA_VERSION in $release")
                val probeMajor = releaseVersion.substringAfter('"').substringBefore('"').substringBefore('.')
                assert(probeMajor == jMajor) { "daemon major $probeMajor != criteria major $jMajor" }
            }
        }
    }

    @Test
    fun `T4 environment promotion is usable end-to-end`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            printJavaHomeTask()
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to j.toString())).use { p ->
                val result = runProbe(p, project, GradleInvocationArguments(envSource = EnvSource.INHERIT))

                assert(result.outcome is BuildOutcome.Success) { "build failed: ${result.consoleOutput}" }
                assert(probeJavaHome(result) == j)
            }
        }
    }

    @Test
    fun `T5 invalid explicit javaHome is terminal`() = runTest(timeout = 300.seconds) {
        val f2 = newNonJdkSentinelDir("t5-f2")
        // I1: an existing regular FILE - it exists but is not a directory, so it fails the
        // documented `exists() && isDirectory` validity check at the isDirectory clause.
        val i1 = SharedTestInfrastructure.sharedWorkingDir.resolve("t5-i1-invalid-explicit")
        Files.writeString(i1, "not a JDK, just a regular file")
        testGradleProject {
            printJavaHomeTask()
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to f2.toString())).use { p ->
                val result = runProbe(
                    p, project,
                    GradleInvocationArguments(javaHome = i1.toString(), envSource = EnvSource.INHERIT)
                )

                // WARN + omit setJavaHome -> Tooling API default -> provider JVM. The WARN emission
                // itself is asserted at Tier 1 (L5); here the wrong branches fail loudly:
                // wrongful env promotion would start the daemon against the non-JDK sentinel F2 and
                // wrongful I1-as-valid would start it against a regular file.
                assert(result.outcome is BuildOutcome.Success) { "build failed: ${result.consoleOutput}" }
                assert(probeJavaHome(result) == j)
            }
        }
    }

    @Test
    fun `T6 malformed daemon criteria file fails closed and surfaces Gradle's error`() = runTest(timeout = 300.seconds) {
        testGradleProject {
            printJavaHomeTask()
            file("gradle/gradle-daemon-jvm.properties", "toolchainVersion=21\\uZZZZ\n")
        }.use { project ->
            providerWith(mapOf("JAVA_HOME" to j.toString())).use { p ->
                val result = runProbe(p, project, GradleInvocationArguments(envSource = EnvSource.INHERIT))

                assert(result.outcome is BuildOutcome.Failed) { "malformed criteria build must fail: ${result.consoleOutput}" }
                assert(result.consoleOutput.contains("gradle-daemon-jvm") || result.outcome.failuresIfFailed?.isNotEmpty() == true) {
                    "expected a Gradle criteria/parse error, got: ${result.consoleOutput}"
                }
            }
        }
    }

    private companion object {
        const val DAEMON_JVM_PROBE_MARKER = "[daemon-jvm-probe]"
    }
}
