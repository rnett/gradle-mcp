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

## More info

- Viewing and debugging dependencies: `gradle_docs(path="userguide/viewing_debugging_dependencies.md")`
- Inspecting toolchains: `gradle_docs(path="userguide/toolchains.md")`
- Running builds and task selection: [Running Builds](running-builds.md)
- Dependency graph audits: [Dependencies](dependencies.md)
- Gradle documentation lookup: `gradle_docs`
