## Context

Nested test builds only run through the Gradle Tooling API, which always spawns daemons and provides no API to stop them after a build (gradle/gradle#8010). Daemons idle out on Gradle's default 3-hour timeout, so test runs leave daemon pools behind. Verified in the current tree:

- `TestGradleProvider.kt:18-25` — `defaultTestGradleSystemProperties` (canonical JVM args: `-Xmx256m`, `org.gradle.workers.max=2`, VFS watch off, config cache on) applied by `withTestGradleDefaults` (line 27). The launcher `javaHome` is NOT pinned, so the inherited `JAVA_HOME` fallback (which differs from the test-worker JDK) can spawn a separate daemon pool; JVM-arg variants likewise create multiple pools.
- `BuildExecutionService.kt:165` — `-Dscan.tag.MCP` is added unconditionally to every launcher; it is common to all nested builds, so the scan tag does not create daemon-pool fragmentation.
- `GradleProvider.kt:104` — `close()` stops running builds and cancels the provider coroutine scope; it cannot stop daemons.
- `TestReportingTest.kt:25/94/146` — three `createTestProvider()` calls create real `DefaultGradleProvider`s that are never closed.
- `GradleProviderTest.kt:50/103/135/170` — the deliberate java-home/JVM-arg variant tests live here.
- Nested test builds run Gradle 9.6.1 (`gradleToolingApi` in `gradle/libs.versions.toml:15`, `BuildConfig.GRADLE_VERSION`), so daemon logs live under `GRADLE_USER_HOME/daemon/9.6.1/`.
- Test projects are config-hash cached at the class/session level (`GradleProjectFixture.kt:34-35`), so project creation is not the daemon-leak source.

## Goals / Non-Goals

**Goals:**

- One canonical daemon identity for nested test builds (single daemon pool per machine).
- Test daemons self-expire quickly (120s) instead of lingering for the default.
- Every real provider created in tests is closed deterministically.
- Phase 0 baseline probes (daemon count, suite wall time, `mcpDependencyReport` launch count) gate verification for the whole A+B+C change set.

**Non-Goals:**

- Isolating test `GRADLE_USER_HOME` (shared user home is standard for this project; separate homes would blow up download volume — deferred as a fallback note only).
- Stopping daemons on provider close (impossible via the Tooling API).
- Touching production source code.

## Decisions

### 1. Canonical daemon identity via fill-in defaults with explicit-variant precedence

**Decision**: `GradleInvocationArguments.withTestGradleDefaults` fills in the launcher `javaHome` with `System.getProperty("java.home")` (the test-worker JDK) only when the caller has not explicitly set one (`javaHome == null`); the `GradleProvider` variant routes every call through the same args-level defaults, so both variants get the fill-in from a single implementation point. An explicit `GradleInvocationArguments.javaHome` always takes precedence and is never overwritten. Add a documented escape hatch (e.g. `pinJavaHome: Boolean = true`) for dedicated fallback tests. Keep the canonical JVM-arg set (`-Xmx256m`, `org.gradle.workers.max=2`, plus existing flags) as the single argument set.

**Variant precedence (override vs bypass)**:
- **Override**: an explicit `javaHome` passed through `withTestGradleDefaults` wins over the canonical fill-in. `GradleProviderTest.kt:50` and `:135` keep their intent unchanged (`:50` passes an explicit home equal to the test-worker JDK; `:135` passes a valid explicit home that must beat an invalid env `JAVA_HOME`).
- **Bypass**: the fallback tests that assert the very resolution paths the canonical default exists to eliminate — `GradleProviderTest.kt:103` (env `JAVA_HOME` fallback) and `:170` (Tooling API default fallback) — opt out of the java-home fill-in via the escape hatch while keeping the canonical JVM-arg set. Their mocked/absent environments resolve to the test-worker JDK, so they exercise the fallback code paths without fragmenting the daemon pool.
- **Ordinary nested builds** never set `javaHome` explicitly, so the canonical default applies to them unconditionally.

**Rationale**: `withTestGradleDefaults` is the single choke point every nested test build routes through (verified: `ConfigurationCacheIntegrationTest`, `BaseReplIntegrationTest`, `GradleProviderTest`, `TestReportingTest`). Because resolution is `args.javaHome ?: env["JAVA_HOME"]` (`BuildExecutionService.kt:168`), filling `javaHome` removes the inherited-`JAVA_HOME` pool split while leaving explicit values intact. One identity + one arg set ⇒ one daemon pool per machine. Variant tests remain confined to `GradleProviderTest.kt:50/103/135/170`.

### 2. Short test-only daemon idle timeout (120s)

**Decision**: Configure `org.gradle.daemon.idletimeout=120000` for nested test builds. The injection point is chosen by a Phase 0 probe task from the candidate options: (a) test-worker JVM system property passed through `org.gradle.jvmargs` in the canonical defaults, (b) a generated `gradle.properties` in test projects via `GradleProjectFixture`, (c) launcher environment. Option (a) is preferred: it lives next to the existing canonical `org.gradle.jvmargs` and requires no fixture changes; if the probe shows it does not reach the daemon JVM, fall back to (b).

**Evidence-driven deviation (implemented, verified 2026-08-04)**: The Phase 0 probe DISPROVED both option (a) and the fallback option (b): `ProviderConnection.initParams` (Gradle 9.6.1) computes daemon parameters from `LayoutToPropertiesConverter`, which applies the user-home `~/.gradle/gradle.properties` LAST — so neither the Tooling API `org.gradle.jvmargs` system property nor a generated project `gradle.properties` reaches the daemon (0 of 1251 daemon logs ever contained `Xmx256m`; probe daemons kept `-Xmx3g`, `idleTimeout=10800000`). The adopted channel is the launcher command-line argument: `withTestGradleDefaults` appends `-Dorg.gradle.daemon.idletimeout=120000` to `additionalArguments`, which `ProviderConnection.initParams` feeds into `InitialPropertiesConverter`/`DaemonBuildOptions` AFTER the gradle.properties conversion (source-verified), setting `DaemonParameters.idleTimeout` -> serialized to the daemon (`DefaultDaemonStarter`) -> `DaemonIdleTimeoutExpirationStrategy`. Probe-verified: daemon context now shows `idleTimeout=120000` + `-Xmx256m` (heap delivered via the Tooling API `addJvmArguments` channel, which replaces the user-home heap; `-D` props on that channel are extracted into build system properties and do NOT affect the daemon JVM). The idle daemon self-terminated between idle+~100s and idle+~175s (2-min probe cadence). Option (b) fixture changes (`GradleProjectFixture` gradle.properties write + `TEST_PROJECT_FIXTURE_SCHEMA` v2 bump) were REVERTED; the ineffective `org.gradle.jvmargs` system property was removed from the canonical defaults. Details: `probes/FINDINGS-task3.1.md`.

**Rationale**: The Tooling API cannot stop daemons, and a teardown `--stop` would also kill non-test user daemons (rejected). The idle timeout is the only supported self-cleanup mechanism. 120s is short enough to guarantee no stragglers between CI runs and long enough to preserve within-suite daemon reuse; active builds are never interrupted.

**Rejected alternatives**:
- Per-project `gradle.properties` written unconditionally by the fixture: pollutes the cached config-hash project dirs and is harder to verify.
- Setting `JAVA_HOME` in the launcher environment instead of pinning `javaHome`: env overrides are less explicit than the launcher API and can be shadowed.
- Teardown `--stop` against the shared `GRADLE_USER_HOME`: stops user daemons too.
- `close()`-stops-daemon: not exposed by the Tooling API.

### 3. Close real providers in tests

**Decision**: Close every real `DefaultGradleProvider` via `use`/`finally`. Concretely, fix `TestReportingTest.kt:25/94/146` and audit the rest of the suite. Document that `close()` (GradleProvider.kt:104) only stops running builds and cancels the provider scope — daemon cleanup is handled by Decision 2.

**Rationale**: Provider close releases build/coroutine resources and is required by the project's testing standards; it is orthogonal to daemon hygiene but part of the same lifecycle contract.

## Risks / Trade-offs

- **Risk**: Filling in `javaHome` changes the JDK nested builds run on for tests that implicitly relied on `JAVA_HOME`. → **Mitigation**: The fill-in uses the test-worker JDK (`System.getProperty("java.home")`), which is what `gradle.properties`-less Tooling API launches effectively should use; variant tests in `GradleProviderTest.kt` remain to assert explicit java-home behavior.
- **Risk**: Fill-in `javaHome` changes what the env-fallback (`:103`) and Tooling-API-default (`:170`) tests exercise. → **Mitigation**: escape hatch preserves their assertions; canonical JVM-arg set still applies; their fallback resolves to the test-worker JDK.
- **Risk**: The 120s idle timeout could cause daemon churn if a test class has >2 min gaps between builds. → **Mitigation**: Within-suite reuse is preserved by the cache (`GradleProjectFixture` config-hash dirs); the probe task records actual gap behavior before finalizing 120s.
- **Risk**: Injection point (a) may not propagate to the daemon JVM. → **Mitigation**: Phase 0 probe verifies the timeout actually reaches the daemon; fall back to (b) generated `gradle.properties`.
- **Trade-off**: No `GRADLE_USER_HOME` isolation means daemon counts are per-machine; the probe quantifies the residual pool so Change B's fork-count decision has data.

## Migration Plan

N/A — test-fixture-only change; no production rollout or rollback concerns.

## Open Questions

- None that would change the specs or task breakdown. The injection-point choice (Decision 2) is resolved by the probe task at implementation time and does not alter the spec contract ("test daemons self-expire within 120s").
