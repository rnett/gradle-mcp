## 1. Phase 0: Baseline Probes (gate for the whole A+B+C change set)

- [x] 1.1 Add a probe mechanism that records: nested daemon count (process list / `GRADLE_USER_HOME/daemon/9.6.1/` log dirs), suite wall time for `integrationTest` + `treeSitterTest`, and `mcpDependencyReport` launch count (daemon log lines) — probe at `probes/daemon-probe.ps1` (snapshot/walltime/count actions)
- [x] 1.2 Run the baseline suite locally and record the numbers in the change notes BEFORE any mechanism is applied
- [x] 1.3 Define pass criteria: no test daemons lingering beyond the 120s idle timeout after the suite; suite wall time not regressed vs baseline; `mcpDependencyReport` launches reduced after Change B

## 2. Standardize Nested-Test Daemon Identity

- [x] 2.1 In `GradleInvocationArguments.withTestGradleDefaults`, fill in (not unconditionally pin) the launcher `javaHome` to `System.getProperty("java.home")` (test-worker JDK) only when the caller has not set one explicitly; implement once so both variants (args-level and `GradleProvider`) inherit the fill-in; an explicit `javaHome` is never overwritten
- [x] 2.2 Add the documented escape hatch parameter (e.g. `pinJavaHome: Boolean = true`) to `withTestGradleDefaults` for dedicated fallback tests
- [x] 2.3 Keep the single canonical JVM-arg set in `defaultTestGradleSystemProperties` (`-Xmx256m`, `org.gradle.workers.max=2`, VFS watch off, config cache on); leave `-Dscan.tag.MCP` (`BuildExecutionService.kt:165`) unchanged
- [x] 2.4 Confirm deliberate java-home/JVM-arg variants remain confined to `GradleProviderTest.kt` (lines ~50/103/135/170): update `:103` (environment `JAVA_HOME` fallback) and `:170` (Tooling API default fallback) to use the escape hatch so they continue exercising the fallback paths; `:50` and `:135` remain unchanged because explicit `javaHome` wins

## 3. Short Test-Only Daemon Idle Timeout

- [x] 3.1 Probe task: verify whether `org.gradle.daemon.idletimeout=120000` reaches the daemon JVM via option (a) test-worker JVM system property in the canonical defaults; fall back to (b) generated `gradle.properties` via `GradleProjectFixture` if the probe fails
- [x] 3.2 Implement the chosen injection point and verify with a probe that an idle test daemon exits within ~120s (daemon log dir / process list)
- [x] 3.3 Keep the rejected alternatives documented in design.md (per-project `gradle.properties` unconditional write, env-var `JAVA_HOME` override, teardown `--stop`, close-stops-daemon)

## 4. Provider Lifecycle: Close Real Providers

- [x] 4.1 Close the three unclosed `DefaultGradleProvider` instances in `TestReportingTest.kt` (lines ~25/94/146) via `use`/`finally`
- [x] 4.2 Audit other tests creating real `DefaultGradleProvider` and ensure deterministic close; note that `close()` only cancels builds/coroutine scope (GradleProvider.kt:104) and does not stop daemons

## 5. Validation

- [x] 5.1 Re-run Phase 0 probes; verify no residual test daemons linger beyond the 120s idle timeout
- [x] 5.2 Run `./gradlew test integrationTest treeSitterTest` (or targeted suites) — no regressions

## 6. Lifecycle

- [x] 6.1 After implementation: apply the change, then archive with spec sync (archive is post-implementation — list as lifecycle expectation only)
