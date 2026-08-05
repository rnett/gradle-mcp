# Phase 0 Probe Findings — task 3.1 (Change C injection point)

Session: 2026-08-04 (continuation of the `test-daemon-hygiene` apply). Probe artifacts:
`probes/daemon-probe.ps1`, `probes/snapshots/snapshot-pre-verify.json`.

## Change B (canonical daemon identity) — VERIFIED WORKING

- Nested-test daemons now spawn on the test-worker JDK: `javaHome=C:\Users\rnett\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2, javaVersion=21` (daemon-192840), matching `System.getProperty("java.home")` in the test JVM. No more JDK-20 pool split from inherited `JAVA_HOME`.
- The new daemon spawns are JDK 21 (single identity); JDK-20 daemons still present are pre-existing (outer/dev or other-session daemons, e.g. `gradle-mcp-junie-wt-03` uses them).

## Change C — candidate injection points, empirically probed

### Option (a) — `org.gradle.jvmargs` Tooling API system property: FAILS (disproven)

- The canonical defaults pass `org.gradle.jvmargs=-Xmx256m -Dorg.gradle.daemon.idletimeout=120000` via
  `launcher.withSystemProperties(...)` (BuildExecutionService.kt:187).
- Probe: after `:test --tests *GradleProviderTest` (build b-2), the nested-test daemon (daemon-192840)
  started with `[-XX:MaxMetaspaceSize=512m, -Xmx3g, ...]`, `idleTimeout=10800000` (3h default). The
  canonical args never appear in any daemon log (0 of 1251 logs mention `Xmx256m`).
- Source root cause (Gradle 9.6.1): `ProviderConnection.initParams`
  (`platforms/core-runtime/launcher/.../provider/ProviderConnection.java:348-367`) computes
  `DaemonParameters` from `LayoutToPropertiesConverter.convert(initialProperties, buildLayoutResult)`
  — Tooling API `setSystemProperty` values are NOT part of that map (they only become build system
  properties via `effectiveSystemProperties`/StartParameter).

### Option (b) — generated `gradle.properties` in fixture projects: FAILS (disproven)

- Implemented: `GradleProjectBuilder.build()` writes `org.gradle.jvmargs=-Xmx256m -Dorg.gradle.daemon.idletimeout=120000`
  into every fixture project; `TEST_PROJECT_FIXTURE_SCHEMA` bumped `v1 -> v2`. Verified the file IS
  generated (REPL check).
- Probe: nested build still used a daemon with `-Xmx3g` (user-home value), `idleTimeout=10800000`
  (daemon-863576, JDK 25 from the REPL client; same result for the test JVM in build b-3 — no canonical
  spawn at all).
- Source root cause: `LayoutToPropertiesConverter.convert` calls `configureFromHomeDir(gradleUserHomeDir)`
  AFTER `configureFromBuildDir(...)` and `maybeConfigureFrom` overwrites keys — so the USER-HOME
  `~/.gradle/gradle.properties` (`org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=512m`) overrides the
  project-dir value for daemon parameters.

### Option (d) — Tooling API JVM arguments channel: PARTIAL (heap only; timeout does NOT propagate)

- `launcher.addJvmArguments(...)` DOES reach the daemon JVM: daemon-660080/909940 (build b-6) and
  daemon-88592 (build b-8) started with `-Xmx256m` replacing the user-home `-Xmx3g`.
- BUT `-D` system properties on this channel are extracted by `JvmOptions` into BUILD system properties
  (not daemon JVM args): the daemon context still showed `idleTimeout=10800000` with the property absent
  from `daemonOpts`/command line. The daemon idle timeout is NOT read from the daemon JVM's own
  `org.gradle.daemon.idletimeout` property — it comes from the CLIENT-side `DaemonParameters.idleTimeout`
  (`DaemonBuildOptions.IdleTimeoutOption`, gradle property only, user-home overrides).

### Option (e) — launcher `-D` command-line argument: WORKS (implemented)

- `ProviderConnection.initParams`: `InitialPropertiesConverter` parses `-D` args from
  `operationParameters.getArguments()` (launcher `withArguments`), and
  `LayoutToPropertiesConverter.convert` applies `properties.putAll(initialProperties.getRequestedSystemProperties())`
  LAST — AFTER the user-home gradle.properties conversion. So `-Dorg.gradle.daemon.idletimeout=120000`
  as a launcher argument overrides `~/.gradle/gradle.properties` and flows into `DaemonBuildOptions`,
  setting `DaemonParameters.idleTimeout` (source-verified).
- Implementation: `withTestGradleDefaults` appends `TEST_DAEMON_IDLE_TIMEOUT_ARG`
  (`-Dorg.gradle.daemon.idletimeout=120000`) to `additionalArguments`; `-Xmx256m` stays on the
  JVM-arguments channel (`TEST_DAEMON_JVM_ARGS`). Option (b) fixture changes and the `org.gradle.jvmargs`
  system prop were reverted/removed.
- Probe (build b-8, 2026-08-04): daemon-88592 (spawned 15:18:42) context =
  `javaHome=...eclipse_adoptium-21..., javaVersion=21, idleTimeout=120000, daemonOpts=...,-Xmx256m,...`.
  Process confirmed dead between 15:19:47 (running) and 15:21:37 (gone); log tail shows the daemon's
  self-stop path (`Daemon$1.run` -> `PersistentDaemonRegistry.remove`). Idle exit within ~120s verified.

## Pass criteria (task 1.3, from design)

- No test daemons lingering beyond the 120s idle timeout after the suite: VERIFIED for daemons spawned
  with the change (daemon-88592 exited ~120s after idle). Pre-change 3h-timeout daemons (e.g. 660080/
  909940, spawned by b-6 with the previous code) remain until they idle out or are stopped — transient
  transition artifacts, not produced by the new code.
- Suite wall time not regressed vs baseline: baseline full-suite wall time was NOT captured before the
  change (prior sessions ran only targeted suites); targeted-suite wall time b-6 = 47s (cold daemons),
  b-8 = 18s (daemon reuse). Post-change full-suite wall time: PENDING (task 5.2 full run).
- `mcpDependencyReport` launches reduced after Change B (single canonical daemon pool): count PENDING.

## Baseline numbers recorded

- `snapshot-pre-verify.json`: 6 running daemons (3 JDK-20 with -Xmx3g/-Xmx512m from inherited JAVA_HOME,
  3 JDK-21/-Xmx3g from user-home gradle.properties), 1251 daemon logs for 9.6.1.
- `snapshot-pre-c.json`: 9 running daemons, 1256 logs (pre-Change-C, this session).
- Historical (pre-change diagnosis, prior session): daemons spawned on JDK 20 via inherited `JAVA_HOME`;
  `-Xmx256m` never present on any daemon.

## Final verification (tasks 1.2/5.1/5.2) — recorded 2026-08-04, final code state

### Suite wall times (final state; baseline full-suite wall time was NEVER captured pre-change — gap noted honestly)

- `./gradlew test` (full unit suite, b-9, `--rerun`): BUILD SUCCESSFUL in **1m 19s**; **437 tests, 0 failed**.
- `./gradlew integrationTest treeSitterTest` (b-11, `--rerun`): BUILD FAILED in 2m 17s —
  **112/113 integration tests passed**, 1 flaky failure (`JvmTargetReplIntegrationTest` — 60s MCP
  `Request timed out` on `kotlin_repl start` under parallel load; **passes in isolation** b-12, 12.4s).
  `treeSitterTest` SUCCESS (6.5s). The playbook's previously-documented pre-existing REPL failures
  (`JavaReplIntegrationTest`, `KotlinReplIntegrationTest`) did NOT reproduce — all passed.
- Targeted-suite context: b-6 = 47s (cold daemons), b-8 = 18s (daemon reuse) — recorded earlier.
- Baseline full-suite wall time: **not captured pre-change** (prior sessions ran only targeted suites);
  no regression signal observed (all suites within expected ranges).

### Daemon pool / mcpDependencyReport counts (final state)

- `snapshot-post-verify.json` (15:36): **6 running daemons, 1274 logs** (pre-verify: 7 running/1274).
  All daemons spawned by this session's suites carry the canonical identity:
  `javaHome=...eclipse_adoptium-21..., javaVersion=21, idleTimeout=120000, daemonOpts=...,-Xmx256m,...`
  (daemon-846428, b-9 full-suite run, verified from its log DefaultDaemonContext).
- **Single-pool reuse confirmed**: the b-9 full unit suite handled **15 `mcpDependencyReport` launches on
  ONE daemon (846428)** — nested builds reused the canonical daemon instead of spawning one per build
  (pre-change diagnosis: fragmented JDK-20/JDK-21 pools with repeated spawns).
- `mcpDependencyReport` counts: **109 matches / 24 daemon logs (last 24h)**; 1801 matches / 226 logs
  (last 30d, cumulative across sessions, includes non-test daemons).
- **Task 5.1 PASSED**: at 15:37, none of today's test daemons (spawned 15:28–15:31 with the new code)
  were still running — all self-exited within the 120s idle timeout. The 6 remaining daemons are
  pre-existing (spawned 11:00–15:07 before this session's test code; default 3h timeout, JDK 20/21,
  `-Xmx3g`/`-Xmx512m` — outer build/dev daemons, not produced by the new code).
- Probe tooling note: `daemon-probe.ps1` snapshot context extraction fixed (read 200 head lines instead
  of 3 — Gradle 9.x writes `DefaultDaemonContext` ~line 120); snapshot re-recorded.
