<!--
class: authored-local
skill: using-gradle
-->
# Running Builds

Expresses Gradle builds through the `gradle` MCP tool in foreground or background, manages build lifecycle, and captures task output.

## Execution Lifecycle

### Foreground (Default)
The preferred mode. Provides progressive disclosure of build output. Use when you intend to wait for a result.

```json
{
  "commandLine": ["clean", "build"]
}
```

### Background
Use `background: true` ONLY for persistent tasks (servers, continuous builds) or performing parallel work.

```json
{
  "commandLine": ["bootRun"],
  "background": true
}
```

Returns a `BuildId` immediately. Use the following monitor sequence:
1. **Wait for Log**: `wait_build(buildId=ID, waitFor="Regex")`
2. **Wait for Task**: `wait_build(buildId=ID, waitForTask=":app:assemble")`
3. **Check Status**: `query_build(buildId=ID)` (non-blocking progress).
4. **Dashboard**: `query_build()` with no arguments to list all builds.
5. **Stop**: `gradle(stopBuildId=ID)` to free resources.

**Anti-pattern**: Leaving background builds running after the task is complete.

### Continuous Builds
Background mode with the `--continuous` flag. Wait for `"Waiting for changes"` via `wait_build`.

## Task Path Syntax (Surgical Home)

Use these verified task-addressing forms in the `gradle` tool's `commandLine` array:

- **Recursive selector**: `task` (no leading colon). Executes the task in every project where it exists (for example, `test`).
- **Root project**: `:task` (for example, `:test`).
- **Specific subproject**: `:app:task` (for example, `:app:test`).

**Anti-pattern**: Guessing task names; always use `help --task <name>` first.

## Underlying CLI Grammar

The underlying Gradle command model is `gradle [tasks...] [options...]`. Options may precede or follow tasks. Use `=` for option values where supported, and keep task-specific options after the task name. In this environment, express that model through the `gradle` MCP tool by passing tasks and options in its `commandLine` array.

```json
{"commandLine":[":app:test","--tests=com.example.UserTest","--console=plain"]}
{"commandLine":[":app:run","--args=--port=8080","--stacktrace"]}
{"commandLine":["--dry-run","build"]}
```

Built-in options control Gradle execution (`--stacktrace`, `--dry-run`, `--build-cache`, `--console=verbose`). Task-specific options belong to the named task (`--tests=...`, `--args=...`); do not treat them as global switches. Direct `./gradlew` or `gradlew.bat` invocation is the documented fallback only when no MCP Gradle tooling is available.

## Inspection Loop

1. Run `tasks` or `:<project>:tasks` through the `gradle` tool's `commandLine` array to discover task groups; use `--all` only when hidden or uncommon tasks matter.
2. Run `help --task <path>` through `commandLine` to read authoritative task options and behavior.
3. Run `--dry-run` (short form `-m`) through `commandLine` against the intended task to preview which tasks **would** run and in what order.
4. Run the task through `commandLine`, then verify transitive prerequisites, task outcomes, and the selected project/variant.

Classify lifecycle tasks such as `build`, `check`, and `assemble` as graph entry points. Classify action tasks such as `compileKotlin`, `test`, or `run` as concrete work; names are plugin/project-specific, so discover them first.

## Lifecycle Task Vocabulary

Use these standard tasks as entry points:

| Task | When to Use |
| :--- | :--- |
| `assemble` | Create artifacts (JARs, WARs) without running tests. |
| `build` | Full verification (assemble + check). |
| `check` | Run all verification tasks (tests, linting). |
| `run` | Execute the main application. |
| `installDist` | Create a local installation directory. |
| `publishToMavenLocal` | Publish artifacts to the local `.m2` cache. |

## Essential Flags

| Flag | Effect | Default / Rule |
| :--- | :--- | :--- |
| `-x <task>` | Excludes a specific task from the graph. | Use to skip heavy tasks (e.g., `-x test`). |
| `--continue` | Continues independent tasks after failure. | Use for failure inventory, not proof that dependent tasks ran. |
| `--dry-run` | Prints the task graph without executing actions. | Use to verify path selectors and task inclusion. |
| `--offline` | Forces use of local cache only. | Use only when network absence is intentional; it may reuse stale dependencies. |
| `--refresh-dependencies` | Forces remote check for dynamic/SNAPSHOT deps. | Use for intentional freshness diagnosis. |
| `--parallel` | Executes independent projects in parallel. | Standard for multi-project builds. |
| `--stacktrace` | Provides full JVM stack traces on failure. | Essential for build-logic debugging. |
| `--info` / `--debug` | Increases log verbosity. | Use `--info` first; `--debug` is extremely noisy. |
| `-P<name>=<val>` | Sets a project property. | Standard way to pass build-time config. |
| `--warning-mode` | Controls deprecation reporting. | Use `all` for triage; use `fail` only as an intentional migration gate. |

**Version notes**: Gradle 9 and 8.x use `--rerun`; Gradle 7.6+ also supports and prefers `--rerun` over `--rerun-tasks`. Gradle 7.0-7.5: use `cleanTest test` if `cleanTest` exists, otherwise use `--rerun-tasks`.

## CLI controls for diagnosis and reproducibility

### Failure evidence ladder

Start with structured `query_build` output and the exact failure or problem record. Escalate only when the evidence is insufficient:

1. Add `--stacktrace` for the failing task's exception path.
2. Add `--info` for task selection, inputs, resolution, and lifecycle detail.
3. Add `--debug` only for a narrowly justified, local diagnostic; it is noisy and may expose sensitive paths or values.
4. Use `--scan` only when publication is explicitly authorized and the scan's terms and destination are acceptable.
5. Add `--warning-mode=all` when deprecations or an upcoming Gradle upgrade are part of the question.

**Anti-pattern:** rerun every failure with `--debug`, or treat a green result with `UP-TO-DATE` or `FROM-CACHE` tasks as proof that the failing action executed.

### Cache and network decision table

| Need | Use | Interpretation |
|---|---|---|
| Test cached-only behavior or operate without network | `--offline` | Cached metadata and artifacts only; success is not proof of fresh resolution. |
| Recheck dependency metadata or dynamic/SNAPSHOT versions | `--refresh-dependencies` | Ask configured repositories again; it does not mean every task reruns. |
| Reuse local or configured remote task outputs | `--build-cache` | Enables build-cache reuse; it is distinct from dependency caching and configuration cache. |
| Force one known task action | `--rerun` where supported | Prefer targeted forcing; use the 7.0-7.5 fallback above. |

**Do this:** choose one control that matches the hypothesis and record `GRADLE_USER_HOME` before comparing cache behavior.

### Console, task options, and selectors

- Use `--console=plain` when raw console output is needed for machine capture; prefer structured `query_build` first.
- Do not use `--quiet` during diagnosis: it suppresses evidence. Use it only when a caller explicitly needs reduced output.
- Put task-specific options after the task they address, such as `:app:test --tests com.example.UserTest` or `:app:run --args=--port=8080`.
- Use `--` to separate Gradle/task options from arguments passed to the task, for example `:app:run -- --port=8080` when the task consumes positional arguments.
- Use full task names in automation. If an abbreviation appears in an existing invocation, validate it with `--dry-run --console=plain` and trace the expanded task before relying on it.

**Anti-pattern:** rely on abbreviated task names in durable agent instructions, treat `--tests` or `--args` as global options, or use `--quiet` while collecting failure evidence.

### Use the daemon

The Gradle daemon is the default and should remain enabled for normal local and CI operation. Use `--no-daemon` only for a documented CI/environment constraint or a controlled comparison; do not reach for it as a speed, cleanliness, or reproducibility habit. Diagnose daemon identity and JVM differences in [Troubleshooting](troubleshooting.md).

**Version notes:** These CLI controls are stable across Gradle 7, 8, and 9, but task-option availability is plugin-specific and console behavior is version-sensitive. For Gradle 7.x, use full task paths and the same narrow controls, then verify the exact wrapper documentation.

**More info**:
- CLI basics/options: `gradle_docs` `tag:userguide`, path `userguide/command_line_interface_basics.md`
- Task basics and graph inspection: `gradle_docs` `tag:userguide`, path `userguide/task_basics.md`
- Incremental outcomes and build cache: `gradle_docs` `tag:userguide`, path `userguide/gradle_optimizations.md`
- MCP execution and monitoring: `gradle` / `captureTaskOutput`; `query_build` / `wait_build`

Cross-references:
- Test selection and failure isolation $\\rightarrow$ [Testing](testing.md)
- Build failure triage and build scans $\\rightarrow$ [Troubleshooting](troubleshooting.md)
- Build environment configuration and property ownership $\\rightarrow$ [Build Environment](build-environment.md)
- Dependency audits $\\rightarrow$ [Dependencies](dependencies.md)
- Official/Internal source research $\\rightarrow$ [Research](research.md)
