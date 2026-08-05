## Why

Nested test builds only run through the Gradle Tooling API, which always spawns daemons and cannot stop them when a provider or connection closes (gradle/gradle#8010). Test daemons inherit Gradle's default multi-hour idle timeout, so each test run leaves daemon pools behind. Daemon identity is also fragmented: the launcher's inherited `JAVA_HOME` fallback differs from the test-worker JDK, and JVM-arg variants create multiple daemon pools, so the suite can accumulate many long-lived daemons. No daemon hygiene is configured anywhere in the tests, and some real providers are never closed.

## What Changes

- Standardize nested-test daemon identity at the `withTestGradleDefaults` choke point in `TestGradleProvider.kt`: pin `javaHome` to the test-worker JDK (`System.getProperty("java.home")`) and keep one canonical JVM-arg set (`-Xmx256m`, `org.gradle.workers.max=2`) so the inherited `JAVA_HOME` fallback stops spawning a separate daemon pool
- Give nested test builds a short test-only daemon idle timeout (`org.gradle.daemon.idletimeout=120000`) so stragglers self-expire instead of lingering for the default
- Close every real `DefaultGradleProvider` in tests via `use`/`finally`; fix the three unclosed providers in `TestReportingTest.kt`
- Add Phase 0 baseline probes (daemon count, suite wall time, `mcpDependencyReport` launch count) that gate verification for the whole change set (A + B + C)

## Capabilities

### New Capabilities
- `test-daemon-hygiene`: Nested-test Gradle daemons use one canonical identity, self-expire after a short test-only idle timeout, and every real `GradleProvider` created by tests is closed deterministically

### Modified Capabilities
- `testing-standards`: "Test Lifecycle & Environment" names `GradleProvider` resource management explicitly and requires real nested builds to route through the standardized test defaults

## Impact

- **Test fixtures**: `src/testFixtures/kotlin/dev/rnett/gradle/mcp/fixtures/gradle/TestGradleProvider.kt` (pin `javaHome`, canonical JVM args), `src/testFixtures/kotlin/dev/rnett/gradle/mcp/fixtures/gradle/GradleProjectFixture.kt` (only if the generated-`gradle.properties` placement for the idle timeout is chosen)
- **Tests**: `src/test/kotlin/dev/rnett/gradle/mcp/gradle/TestReportingTest.kt` (close 3 unclosed providers), `src/test/kotlin/dev/rnett/gradle/mcp/gradle/GradleProviderTest.kt` (deliberate java-home/JVM-arg variants stay confined here)
- **Build config**: `build.gradle.kts` only if the test-worker JVM system-property placement for the idle timeout is chosen
- **No production code changes**
