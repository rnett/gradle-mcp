<!--
class: authored-local
skill: using-gradle
-->
# Build Orientation

Use this reference when you first encounter an existing Gradle checkout. Identify the filesystem markers, read settings before interpreting project paths, then inspect the applied plugins and configurations that explain the build.

## First-contact markers

| Marker | What it tells you |
|---|---|
| `gradlew`, `gradlew.bat` | The repository's wrapper entry points and documented CLI fallback when no MCP Gradle tooling is available. |
| `gradle/wrapper/` | Wrapper implementation and `gradle-wrapper.properties`, including the declared Gradle distribution and optional checksum. |
| `settings.gradle` / `settings.gradle.kts` | The build entry point: root identity, included projects, plugin resolution, dependency repositories, catalogs, and included builds. |
| Root `build.gradle` / `build.gradle.kts` | Configuration for the root project; do not assume it defines every project or task. |
| Per-project `build.gradle` / `build.gradle.kts` | Configuration for one included project, including applied plugins, dependencies, extensions, and tasks. |
| `src/` | Conventional source and test roots for a project; confirm the applied plugin and source-set layout before assuming names. |
| `gradle.properties` | Project or user properties and Gradle runtime settings; inspect both checkout and Gradle User Home locations. |
| `gradle/libs.versions.toml` | The conventional version catalog, if present; generated `libs` accessors are available only when the wrapper and build enable catalogs. |

**Do this:** read `gradle/wrapper/gradle-wrapper.properties`, then `settings.gradle(.kts)`, then the root and relevant project build files. Use `:projects`, `:tasks`, and `:properties` to confirm the evaluated model.

## Generated state and Gradle User Home

Treat generated directories as evidence-bearing state, not source files:

| Location | Meaning | Operator rule |
|---|---|---|
| Project `.gradle/` | Project-local Gradle state, including caches and configuration-related metadata. | Inspect the affected entry before deleting; delete only as a targeted diagnosis. Never commit it. |
| Project `build/` | Disposable task outputs and reports. | Inspect reports and outputs before deleting; regenerate only when the diagnosis requires it. Never commit it. |
| `GRADLE_USER_HOME` | The cache, daemon, wrapper-distribution, global-configuration, and downloaded-JDK universe for the invoking user home. | Record it in every cache or daemon comparison; do not confuse it with `GRADLE_HOME` or delete the entire directory for one symptom. |

**Do this:** identify whether a symptom is project-local or user-home-wide before removing state. Use [Build Environment](build-environment.md) for property and environment ownership, and [Troubleshooting](troubleshooting.md) for targeted cache and daemon cleanup.

**Anti-pattern:** delete `.gradle/`, `build/`, or all of `GRADLE_USER_HOME` first and call the resulting build a diagnosis. Deletion can remove the evidence and change dependency, wrapper, and daemon behavior at once.

**Version notes:** Layout is stable across Gradle 7, 8, and 9. Cache marking is documented from Gradle 8.1 and configurable cleanup from 8.0; for Gradle 7.x, expect default fixed cleanup behavior and avoid applying newer cleanup configuration assumptions.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/directory_layout.md`, terms `Project cache cleanup`, `Gradle User Home directory`

## Project model and evaluation order

A Gradle build contains one or more projects. `settings.gradle(.kts)` defines the build structure, including `rootProject.name` and `include(...)` project paths. Read it **before** interpreting `:project:task` selectors or directory hierarchy.

- A single-project build may omit a settings file; Gradle derives a default structure.
- A multi-project build requires settings to include its projects. A missing or empty settings file is diagnostic evidence that paths may resolve as a different, single-project build.
- Settings are evaluated first. Put build-wide concerns there: `pluginManagement`, `dependencyResolutionManagement`, repository policy, version catalogs, and included builds.
- Build scripts are evaluated for individual projects. They apply plugins and configure that project's extensions, configurations, dependencies, and tasks.

Groovy and Kotlin DSL files are both stable across Gradle 7, 8, and 9. Match the existing extension (`.gradle` or `.gradle.kts`) and do not translate syntax while diagnosing an operational issue.

## Dependencies and plugins: classify before changing

Separate build-script/build-logic dependencies from project dependencies:

| Scope | Inspect as | Do not confuse with |
|---|---|---|
| Build script or `build-logic` dependency | Plugin/build-logic classpath and `buildEnvironment` | The application or library's compile/runtime classpath |
| Project dependency | A project configuration such as `api`, `implementation`, or `runtimeOnly` | The classpath used to compile or configure the build itself |

Inspect applied plugins before changing task or dependency configuration. Plugins add tasks, configurations, extensions, and conventions; use `plugins`, `tasks`, `properties`, and `buildEnvironment` to identify what is actually present. Route deeper plugin-origin and source analysis to [Research](research.md).

## Invocation boundary

Drive builds through the `gradle` MCP tool. Its `commandLine` array expresses the underlying Gradle CLI command model, including task paths and options. Use direct `./gradlew` or `gradlew.bat` only as a documented fallback when no MCP Gradle tooling is available. IDE import and synchronization is a separate concern: an IDE may model or invoke Gradle, but it is not the operator's authoritative build boundary. Do not infer task availability or build success from IDE state.

**Version notes:** Settings/build-file roles and Groovy/Kotlin DSL remain stable across Gradle 7/8/9. Version catalogs require Gradle 7.4+; for older wrappers follow the compatibility fallback in [SKILL.md](../SKILL.md). Plugin-created configurations and source-set names remain plugin-, variant-, and target-dependent; discover them instead of assuming Java names.

**More info:**
- Core concepts: `gradle_docs` `tag:userguide`, path `userguide/gradle_basics.md`
- Settings: `gradle_docs` `tag:userguide`, path `userguide/settings_file_basics.md`
- Build files: `gradle_docs` `tag:userguide`, path `userguide/build_file_basics.md`
- Plugins: `gradle_docs` `tag:userguide`, path `userguide/plugin_basics.md`
- Project mapping and task inspection: `gradle` and `captureTaskOutput`

**Cross-references:**
- Run and inspect tasks -> [Running Builds](running-builds.md)
- Inspect repositories and configurations -> [Dependencies](dependencies.md)
- Research applied plugins -> [Research](research.md)
- Diagnose daemon and property state -> [Troubleshooting](troubleshooting.md)
