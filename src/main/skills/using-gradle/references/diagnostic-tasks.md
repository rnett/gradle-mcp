# Diagnostic Task Coverage

Use-case matrix of the CORE Gradle diagnostic (reporting) tasks, the discovery rule for plugin-provided reports, and how to choose the right task for the question you are asking. These tasks inspect build/model state without modifying it; run them through the `gradle` MCP tool's `commandLine` array and read the output with `query_build`.

## When to use this reference

Reach for this reference when diagnosing a build issue or choosing a reporting task. If you do not know whether a task exists, apply the discovery rule below before inventing a name.

## Use-case matrix: question -> task

| Question you are asking | Task to run | What it reports |
| :--- | :--- | :--- |
| What does this build contain / what modules exist? | `projects` | The project hierarchy and included builds. |
| What tasks are available? | `tasks` (omit `--all` first) | Task groups and descriptions; `--all` reveals hidden tasks. |
| What does a specific task do and what options does it take? | `help --task <name>` | Authoritative task options, dependencies, and behavior. |
| What project properties are set and from where? | `properties` | Project properties; use `--property <name>` where supported. |
| What is the resolved dependency tree for a configuration? | `dependencies --configuration <name>` | The resolved graph for a configuration (e.g. `runtimeClasspath`). |
| Why was a specific module/version selected? | `dependencyInsight --dependency <g>:<a> --configuration <name>` | The resolution path and winner for one dependency. |
| What is on the build/script classpath? | `buildEnvironment` | The classpath of the build itself (build scripts and buildSrc). |
| What variants does a project expose, and with what attributes? | `outgoingVariants` | The outgoing (consumable) variants and their attributes. |
| What configurations exist and how are they used? | `resolvableConfigurations` | The resolvable configurations and their declared usage/role. |
| Which JDKs are available and which toolchain would be selected? | `javaToolchains` | Detected toolchains, auto-provisioning status, and compatibility. |
| What is on the build/script classpath for the active JDK/daemon environment? | `javaToolchains`, `buildEnvironment`, `--version` | JDK, daemon, toolchain, IDE, and CLI state; selections may differ across contexts. |
| Which project or plugin owns a task in a complex build? | `projects` -> `tasks --all` -> `help --task` | Project hierarchy first, then task list for the project scope, then authoritative task details. |

## Discovery rule for plugin-provided reports

Core Gradle provides only the diagnostic set above. Plugins add their own reporting tasks (for example, a plugin-specific dependency, component, or build report). To discover them, do **not** guess: run `tasks --all` (or `help --task <name>` when you believe one exists) and read the actual task list.

```text
:gradle --tasks --all        # list every task, including hidden/plugin-provided reports
:gradle --help --task <name> # read one task's options and description
```

**Default:** run `tasks --all` once to learn the plugin-contributed report names, then use `help --task <name>` to confirm each before invoking it.

**Anti-pattern:** assume a plugin exposes a report under a guessed name, or paste a report name from an older Gradle/plugin version without verifying it against `tasks --all`.

## Verifying third-party plugin-produced artifacts

For artifacts produced by third-party packaging plugins (for example Shadow, Vanniktech publishing, or BuildConfig), do not assume the plugin DSL or publication names. Inspect the applied plugin and its contributed tasks and configurations, then verify the component model with `outgoingVariants` and the relevant resolvable-configuration reports. For publications, run `publishToMavenLocal` and inspect its output — the POM, Gradle Module Metadata, and artifact names — as evidence.

## Scope and non-goals

This reference does **not** exhaustively enumerate plugin-contributed tasks. Plugin task sets vary by plugin and Gradle version; the discovery rule is the general mechanism. For custom `@CacheableTask`/input modeling of reports you author yourself, route to `authoring-gradle-builds`.

**Version notes:** The core diagnostic task set is stable across Gradle 7, 8, and 9, but option spelling (e.g. `--configuration`, `--property`) and plugin-contributed task names are version- and plugin-sensitive. Read `gradle/wrapper/gradle-wrapper.properties` before a version-sensitive invocation.

## Interpreting completed-build intelligence

The DASHBOARD summary reports a frozen snapshot of the completed build in `Work:`:

- `Work:` shows `configuration`, `dependency-resolution`, and `task-execution` as `completed/total` counts. These are detached from live progress state: they describe the finished build only.
- `Configuration Cache Report:` is a nullable pointer to the configuration-cache report location when the build produced one. Treat it as a verbatim report location and route structured problem diagnosis through `query_build(kind="PROBLEMS")`; do not ask the MCP server to open or parse the report.

Task origin aggregation is available only in `query_build(kind="TASKS")` output:

- `Task Origins:` groups completed tasks by their origin plugin, with the reserved key `_unknown` for tasks that lack provenance. `_unknown` is absent when every task has provenance, and the values sum to the total completed task count.

In `query_build(kind="TASKS")` output, a task's `Reason:` line holds the verbatim Gradle skip message only when the task was skipped:

- `NO-SOURCE` — the task was skipped because it had no source; `Reason: NO-SOURCE` is printed (outcome stays `NO_SOURCE`, never collapsed to `SKIPPED`).
- `SKIPPED` — the task was skipped for any other reason; `Reason: <skipMessage>` is printed verbatim, e.g. `Reason: OnlyIf / disabled`.
- `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, and `UP_TO_DATE` tasks carry no `Reason:` line; for from-cache and up-to-date outcomes the outcome enum alone is sufficient.

Read outcome, reason, and provenance together to explain reused or skipped work.

## More info

- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Inspecting toolchains: `gradle_docs(path="userguide/toolchains.md")`
- Running builds and task selection: [Running Builds](running-builds.md)
- Dependency graph audits: [Dependencies](dependencies.md)
- Gradle documentation lookup: `gradle_docs`
