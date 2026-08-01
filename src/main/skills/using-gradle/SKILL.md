---
name: using-gradle
description: |
  Schedules and performs root-level Gradle operations for inspecting and operating existing builds.

  ## Positive Triggers (when to activate)
  - Mapping the project hierarchy, discovering runnable tasks, or inspecting project properties.
  - Executing Gradle tasks in foreground or background.
  - Monitoring build progress or capturing isolated task output.
  - Diagnosing build failures through filtered test execution or diagnostic tasks.
  - Researching official Gradle documentation, release notes, or internal APIs.
  - Auditing the dependency graph, resolving version conflicts, or discovering library updates.
  - Searching and reading source code for dependencies, plugins, or Gradle itself.
  - Performing trivial everyday dependency edits (adding a version-catalog entry + library, bumping a version).

  ## Negative Triggers (when NOT to activate)
  - Structural build authoring: adding/changing plugins, repositories, modules/subprojects, toolchains, publishing, CI wiring, compiler options, or testing frameworks (use `authoring-gradle-builds`).
  - Executing arbitrary Kotlin/Java code via the REPL (use `interacting-with-project-runtime`).
  - Rendering Compose UI components (use `verifying-compose-ui`).
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.1.0"
---
<!--
class: authored-local
skill: using-gradle
-->

# Authoritative Gradle Build Execution, Testing & Inspection

Inspects, executes, diagnoses, and researches existing Gradle builds using managed orchestration and structured diagnostics.

**More info**: Official Gradle guidance: `gradle_docs` with `tag:userguide` and the topic path; read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive research. Published MCP tools: https://gradle-mcp.rnett.dev/latest/tools/.

## Constitution

- **ALWAYS** use the `gradle` tool instead of `./gradlew` via shell.
- **ALWAYS** prefer foreground execution; use background only for persistent (servers) or parallel work.
- **STRONGLY PREFERRED**: Use `query_build` for all diagnostics; avoid raw console parsing.
- **ALWAYS** use `query_build(kind="TESTS")` for test output; **NEVER** use `captureTaskOutput` for tests.
- **NEVER** use `--rerun-tasks` unless investigating project-wide cache corruption; prefer `--rerun` for targeted task forcing.
- **ALWAYS** read the task outcome (`UP-TO-DATE`, `FROM-CACHE`, `SKIPPED`, etc.); a green result with zero execution is unproven.
- **ALWAYS** read the wrapper version (`gradle/wrapper/gradle-wrapper.properties`) before applying version-specific advice.
- **NEVER** guess task names; use `help --task <name>` for authoritative documentation.
- **Handoff**: Route structural build edits, compiler-option configuration, and testing-framework configuration to `authoring-gradle-builds`.

## First Contact with a Build

1. **Version Check**: Read `gradle/wrapper/gradle-wrapper.properties` then consult the Compatibility Reference below.
2. **Build Orientation**: Recognize wrapper, settings, build-script, source, properties, and catalog markers; read `settings.gradle(.kts)` and relevant build files before interpreting hierarchy or task paths. Load [Build Orientation](references/build-orientation.md) for the filesystem and project model.
3. **Environment Baseline**: Record wrapper version, `GRADLE_USER_HOME`, relevant property sources, JVM owners, and safe environment metadata before comparing runs. Load [Build Environment](references/build-environment.md) for precedence, properties, environment variables, proxies, and init-script detection.
4. **Hierarchy Map**: Run `:projects` to discover all modules.
5. **Task Discovery**: Run `:tasks` for the root, or `:<project>:tasks` using a real project path from `:projects`; omit `--all` initially.
6. **Property Inspection**: Use `:properties` for the root or `:<project>:properties` for a discovered project. Use `--property <name>` where supported; otherwise run the properties task and filter the output.
7. **Entry Points**:
   - `build`: Assembles and verifies the project (assemble + check).
   - `check`: Primary verification task.
   - `:<project>:test --tests <X>`: Targeted test execution using a project path discovered from `:projects`.
   - `run` / `installDist`: Runtime execution.

## Compatibility Quick-Reference

| Feature | Gradle 9 | Gradle 8.x | Gradle 7.x | Fallback / Rule |
| :--- | :--- | :--- | :--- | :--- |
| `--rerun` | Yes | Yes | 7.6+ | 7.0-7.5: Use `cleanTest test` or `--rerun-tasks`. |
| Catalogs | Yes | Yes | 7.4+ | < 7.4: Use existing `buildSrc`, scripts, or `ext`. |
| Config Cache | Stable, opt-in | Stable from 8.1; 8.0 pre-stable | Incubating/experimental, opt-in | < 8.1: Use for explicit investigation only; inspect `PROBLEMS` and the HTML report. |
| Run JVM | 17+ | 8+ | 8+ | For compile/test compatibility, use a toolchain and consult `gradle_docs` or the exact Gradle compatibility matrix; do not infer it from this minimum. |
| Build Scan | Yes | Yes | Yes | `--scan` may prompt for terms of service. |
| `properties --property <name>` | Yes | Current 8.x docs | Exact 7.x availability unverified | If unsupported, run the properties task and filter the captured output. |

## Everyday Dependency Edits

1. **Add Entry**: Add version and library to `gradle/libs.versions.toml`.
2. **Declare**: Add dependency to `build.gradle.kts` (e.g., `implementation(libs.library.name)`).
3. **Verify**: Run `inspect_dependencies` to confirm resolution.
*Anything structural (plugins, repositories, modules, toolchains, publishing, CI, compiler options, or testing frameworks) $\rightarrow$ hand off to `authoring-gradle-builds`.*

## Reference Discovery

Read the linked references as part of the workflow: use [Build Orientation](references/build-orientation.md) and [Build Environment](references/build-environment.md) when first orienting yourself or resolving environment inputs; use [Running Builds](references/running-builds.md) for foreground or background lifecycle execution, recursive or absolute task-path selection, and isolated task output; use [Testing](references/testing.md) for filtered class or method runs, failure isolation, and targeted reruns across Gradle versions; use [Troubleshooting](references/troubleshooting.md) for configuration or compilation failures, configuration-cache diagnosis, daemon/JVM/`JAVA_HOME`/memory issues, and build-scan or deprecation diagnostics; use [Dependencies](references/dependencies.md) for compile/runtime/test configuration scoping, resolved-graph audits, version conflicts, and stable updates; and use [Research](references/research.md) for version-aware official Gradle documentation, Gradle internals and lifecycle, and dependency, plugin, or JDK source research.

## Cross-Skill Handoffs

- **Structural Build Changes** (plugins, repositories, modules, toolchains, publishing, CI, compiler options, testing frameworks) $\rightarrow$ `authoring-gradle-builds`.
- **Runtime Logic Probing** (JVM/Kotlin REPL) $\rightarrow$ `interacting-with-project-runtime`.
- **UI Verification** (Compose) $\rightarrow$ `verifying-compose-ui`.

## Workflows

### Investigative Loop
1. Execute build/test via [Running Builds](references/running-builds.md).
2. Diagnose failures via [Troubleshooting](references/troubleshooting.md) or [Testing](references/testing.md).
3. Inspect conflicts via [Dependencies](references/dependencies.md).
4. Read source via [Research](references/research.md).

1. Identify missing/incorrect config using inspection tools.
2. If the change is a trivial dependency edit, update the version-catalog entry and library declaration in this skill, then verify resolution with `inspect_dependencies`.
3. If the change is structural (plugins, repositories, modules/subprojects, toolchains, publishing, CI, compiler options, or testing frameworks), hand off to `authoring-gradle-builds`.
4. Verify the fix with a fresh build.

### Everyday Dependency Edit
1. Update `libs.versions.toml` $\rightarrow$ 2. Update `build.gradle.kts` $\rightarrow$ 3. Verify via `inspect_dependencies`.
