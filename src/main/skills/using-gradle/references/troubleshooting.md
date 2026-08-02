# Troubleshooting

Triage build failures and diagnose environmental anomalies using structured MCP diagnostics and Gradle lifecycle controls.

## Failure Triage Workflow

When a build fails, execute these diagnostic calls in order:

1. **Check Failure Summary**: Call `query_build(kind="FAILURES")` to identify the specific `FailureId`.
2. **Identify Problems**: Call `query_build(kind="PROBLEMS")` to find compilation or configuration problems.
3. **Inspect Console**: Call `query_build(kind="CONSOLE")` to see the final output for environment errors.
4. **Surgical Detail**: Query the exact `FailureId` or `ProblemId` returned by the steps above for the full stack trace.

If structured evidence is insufficient, escalate the rerun with `--stacktrace`, then `--info`; use `--debug` only for a narrowly justified local diagnosis, and use `--scan` only with publication authorization. Add `--warning-mode=all` when deprecations matter.

**Anti-pattern:** begin every diagnosis with `--debug` or publish a scan before checking structured failure and problem records.

## Failure Taxonomy

| Type | Signal | First Move |
| :--- | :--- | :--- |
| **Configuration** | `BUILD` failed during configuration phase; "Configuration cache" errors | `query_build(kind="PROBLEMS")` |
| **Dependency** | `Could not resolve all dependencies`; "Could not find" | `inspect_dependencies` (see [Dependencies](dependencies.md)) |
| **Compilation** | "Compilation failed"; symbols not found; syntax errors | `query_build(kind="PROBLEMS")` |
| **Test** | `BUILD` failed during `:test` task | `query_build(kind="TESTS")` (see [Testing](testing.md)) |
| **Environment** | `JAVA_HOME` not set; "Unsupported class file major version" | Check `JAVA_HOME` and JVM-to-run matrix |

## Configuration Cache Diagnostics

Do not use `:help --configuration-cache` as a health check. Use the representative task or test workflow:

1. Execute it with `--configuration-cache`; record whether the cache was reused or stored.
2. Query `query_build(kind="PROBLEMS", buildId=ID)` for cache problems.
3. Review `build/reports/configuration-cache/<hash>/configuration-cache-report.html`.
4. Use `--configuration-cache-problems=warn` only as a temporary compatibility survey, not a permanent suppression. A representative task can still pass while an incompatible task causes its cache entry to be discarded.

A cache entry is reused only when the requested configuration and relevant inputs are compatible. Environment, properties, files, Gradle version, and build logic can invalidate reuse; unmodeled environment changes can also make reuse appear stale.

**Wrapper check:** Read `gradle/wrapper/gradle-wrapper.properties` before applying this configuration-cache guidance. `--configuration-cache-problems=warn` is a diagnostic mode, not proof that the cache was stored.

## Environment and Daemon Control

### JVM-to-Run Matrix

The JVM running Gradle is distinct from the project's compile/test toolchain and from test workers. Before choosing `JAVA_HOME`, look up the exact Java-version range, including both minimum and maximum, supported by the project's specific Gradle version in the official compatibility matrix: `gradle_docs` with `tag:userguide`, path `userguide/compatibility.md`. Bounds exist on both ends and vary by Gradle minor version. Do not choose `JAVA_HOME` from `sourceCompatibility`, or change the toolchain to fix a launcher incompatibility.

### Daemon identity and lifecycle diagnosis

- Keep the daemon enabled for normal local and CI operation. Use `--no-daemon` only for a documented CI/environment constraint or a controlled comparison.
- A daemon is reusable only within a compatible identity scope. A changed Gradle version, Java home, or daemon JVM arguments can select a different daemon scope or a single-use daemon. Do not conclude that the daemon is disabled merely because one invocation used a disposable daemon.
- Run `--status` to inspect daemons for the matching Gradle version. Run `--stop` only to isolate stale state or free evidenced resources, then rerun the original invocation. Inspect daemon logs and restart only after recording the failing evidence.
- `--status` and `--stop` are scoped to the Gradle version that handles the invocation. A daemon from another wrapper version is not evidence about this build; record the exact wrapper before using either command.
- Daemons normally clean up idle or memory-pressured processes; current guidance says idle daemons stop after about three hours. Treat manual deletion as a targeted recovery, not routine tuning.
- If a daemon disappears, diagnose OS memory-pressure events, daemon crash files, and competing JVMs before increasing heap settings.

**Anti-pattern:** kill arbitrary JVMs, use `--stop` as a first-line fix, or compare daemon behavior without recording wrapper version, Java home, JVM args, and `GRADLE_USER_HOME`.

### JVM ownership

`org.gradle.jvmargs` controls the Gradle build/daemon JVM. `JAVA_OPTS` controls the client JVM. `GRADLE_OPTS` is a Gradle process-startup channel and is not a substitute for `org.gradle.jvmargs`. Forked test workers, application JVMs, compilers, and project toolchains have separate settings. Use [Build Environment](build-environment.md) for the precedence table, environment-variable meanings, proxy properties, and the complete ownership matrix.

**Do this:** identify the failing process first, then change only that process's setting. Keep launcher Java compatibility, daemon heap, compile toolchain, test-worker memory, and application runtime as separate hypotheses.

### User-home and cache state

Record `GRADLE_USER_HOME` before every cache or daemon comparison. It owns dependency caches, build-cache state, wrapper distributions, daemon registry/logs, global `gradle.properties`, init scripts, and downloaded JDKs. Two runs with different user homes do not share the same cache or daemon universe.

- Inspect project `.gradle/` and `build/` before deletion; route layout details to [Build Orientation](build-orientation.md).
- Prefer targeted cache diagnosis. Do not delete all of `GRADLE_USER_HOME` to repair one resolution or daemon symptom.
- Multiple Gradle versions can share a user home, but retention behavior is version-sensitive. Cache tagging is documented from Gradle 8.1 and configurable cleanup from 8.0; Gradle 7.x falls back to fixed default cleanup and does not support newer cleanup assumptions.

**Anti-pattern:** share a customized Gradle 8/9 user home with older wrappers without checking retention compatibility, or treat a clean user home as equivalent to a clean checkout. Do not trust a shared writable cache across trust boundaries; isolate user homes or use a cache whose ownership and provenance are controlled.

**Version notes:** Daemon controls exist across Gradle 7, 8, and 9. Current Gradle 9 guidance includes newer daemon JVM criteria; for Gradle 7.x, use `JAVA_HOME`, `org.gradle.java.home`, and project toolchains, then verify the wrapper-specific compatibility page.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/gradle_daemon.md`, terms `Compatibility`, `Check Daemon status`, `Stop Daemon`, `Daemon Logs`
- `gradle_docs`: `tag:userguide`, path `userguide/directory_layout.md`, terms `Cleanup of caches and distributions`, `Multiple versions of Gradle sharing a Gradle User Home`
- JVM/property ownership and secure environment handling: [Build Environment](build-environment.md)

## Wrapper Integrity

**Wrapper check:** Read `gradle/wrapper/gradle-wrapper.properties` before applying this version-sensitive guidance. Before trusting a downloaded distribution, verify that `distributionSha256Sum` is present and matches the checksum published for the exact wrapper distribution. A version-only URL is not a supply-chain guarantee. Partial selectors such as `9` or `9.1` are moving versions, not pins. Pin the full version and validate the Wrapper JAR checksum in CI. If any checksum is absent or mismatched, stop and repair the wrapper configuration rather than bypassing verification.

```properties
distributionUrl=https\\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
distributionSha256Sum=<checksum-for-the-exact-distribution>
```

## Configuration and execution switches

**Wrapper check:** Read `gradle/wrapper/gradle-wrapper.properties` before applying this version-sensitive guidance. Configuration-on-demand is not a universal speed switch because partial configuration can be incorrect for builds that rely on cross-project configuration. Isolated projects is experimental; use it only when the exact wrapper documentation and build compatibility support it.

## Build Scans and Deprecations

| Situation | Action |
| :--- | :--- |
| Publication is explicitly authorized | Use `--scan`, complete the consent prompt if shown, and capture the resulting scan URL from the build output. |
| A configured Develocity server is explicitly approved | Use the configured server or its documented `--develocity-url` setting; verify the wrapper/plugin documentation before overriding the endpoint. |
| Publication is disallowed or authorization is absent | Do not use `--scan`; prefer `query_build`, `--console=verbose`, `--info`, and local reports. |

A Build Scan captures build metadata for troubleshooting, collaboration, and performance analysis. Treat the Terms of Service prompt and publication destination as policy boundaries; never publish by default.

- **Deprecations**: Use `--warning-mode=all` for evidence and classify whether the warning belongs to the task, plugin, or build logic. `--warning-mode=fail` changes warnings into a failed result, so use it only as an intentional migration gate.

**Version notes**: Gradle 7 environments may use historical Gradle Enterprise terminology. Gradle 8/9 Develocity plugin, DSL, endpoint, and consent properties are version-sensitive; verify the exact wrapper and plugin documentation. Warning modes are standard CLI behavior across Gradle 7/8/9.

**More info**:
- Failure flags and `--continue`: `gradle_docs` `tag:userguide`, path `userguide/command_line_interface.md`
- Configuration cache and diagnostics: `gradle_docs` `tag:userguide`, path `userguide/configuration_cache.md`
- Configuration-cache debugging: `gradle_docs` `tag:userguide`, path `userguide/configuration_cache_debugging.md`
- Daemon and JVM compatibility: `gradle_docs` `tag:userguide`, path `userguide/gradle_daemon.md`
- JVM compatibility: `gradle_docs` `tag:userguide`, path `userguide/compatibility.md`
- Build scans: `gradle_docs` `tag:userguide`, path `userguide/build_scans.md`
- Wrapper integrity: `gradle_docs` `tag:userguide`, path `userguide/gradle_wrapper.md`

**Wrapper-version caveat**: These `gradle_docs` hints are version-scoped; read the wrapper version before applying compatibility or CLI guidance.

For task-path syntax, see [Running Builds](running-builds.md).
