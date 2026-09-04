---
name: using-gradle
description: Using Gradle MCP tools to inspect and run existing builds, including projects, tasks, properties, dependencies, and build results. Activate for Gradle build operation and diagnosis; use authoring-gradle-builds when the build definition itself must change.
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.3.0"
---

# Authoritative Gradle Build Execution, Testing & Inspection

Inspects, executes, diagnoses, and researches existing Gradle builds using managed orchestration and structured diagnostics.

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
- Advanced dependency engineering — variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution/composite builds, or dependency governance (use `advanced-gradle-dependencies`). Everyday dependency inspection, conflict analysis, and update discovery stay here.

**More info**: Search official guidance with `gradle_docs(query="tag:userguide <term>")`; read `gradle/wrapper/gradle-wrapper.properties` before version-sensitive research.

## Constitution

- **ALWAYS** use the `gradle` tool instead of `./gradlew` via shell.
- **ALWAYS** prefer foreground execution; use background only for persistent (servers) or parallel work.
- **STRONGLY PREFERRED**: Use `query_build` for all diagnostics; avoid raw console parsing.
- **ALWAYS** use `query_build(kind="TESTS")` for test output; **NEVER** use `captureTaskOutput` for tests.
- **NEVER** use `--rerun-tasks` unless investigating project-wide cache corruption; prefer `--rerun` for targeted task forcing.
- **ALWAYS** read the task outcome (`UP-TO-DATE`, `FROM-CACHE`, `SKIPPED`, etc.); a green result with zero execution is unproven.
- **ALWAYS** read the wrapper version (`gradle/wrapper/gradle-wrapper.properties`) before applying version-specific advice.
- **Before you start, prioritize structured problems.** When diagnosing a FAILED build or a low-signal error, call `query_build(kind="PROBLEMS")` BEFORE reading build files; PROBLEMS returns identifiers, severity, documentation, occurrence details, and potential solutions. Start file inspection only when structured problems do not resolve the diagnosis.
- **Handoff**: Route structural build edits, compiler-option configuration, and testing-framework configuration to `authoring-gradle-builds`; see [Cross-Skill Handoffs](#cross-skill-handoffs).

## Always-Loaded Operational Footguns

These rules are intentionally compact. Follow the linked authored reference for the evidence, snippets, and version-scoped `gradle_docs` guidance.

- **Model initialization, configuration, and execution separately.** Read settings and build structure as initialization, distinguish model construction from selected task actions, and do not treat configuration output as proof of execution. See [Running Builds](references/running-builds.md).
- **Interpret task outcome, reason, and provenance together.** A green build can perform no action. For reused or skipped work, read the TASKS outcome plus `Reason:` and `Provenance:` together: `FROM_CACHE` and `UP_TO_DATE` outcomes carry no `Reason:` line, while `NO-SOURCE` and `SKIPPED` print the verbatim skip reason (e.g. `Reason: NO-SOURCE`, `Reason: OnlyIf / disabled`). See [Running Builds](references/running-builds.md) and [Diagnostic Tasks](references/diagnostic-tasks.md).
- **Treat phase counts as a frozen completed-build snapshot.** DASHBOARD `Work:` shows `configuration`, `dependency-resolution`, and `task-execution` completed/total counts detached from live progress state. Task origin aggregation is available only in `query_build(kind="TASKS")` output as `Task Origins:` grouping completed tasks by origin plugin with `_unknown` for tasks lacking provenance. Use these to explain where work occurred, and the nullable `Configuration Cache Report:` pointer as a verbatim report location (never ask the MCP server to parse it). See [Diagnostic Tasks](references/diagnostic-tasks.md).
- **Use `--continue` only for failure inventory.** It can run independent tasks after a failure, but dependent work is not proof of success. See [Running Builds](references/running-builds.md).
- **Use `--offline` only when cached-only operation is intentional.** It can reuse stale metadata and artifacts; distinguish dependency-cache state from task and configuration-cache state. See [Dependencies](references/dependencies.md).
- **Respect dependency cache TTL versus `--refresh-dependencies` (version-sensitive).** Read the wrapper first; the default dynamic/changing-module TTL is distinct from an intentional metadata refresh, and refresh does not rerun every task. See [Dependencies](references/dependencies.md).
- **JDK/daemon questions route through existing diagnostics.** Use `javaToolchains`, `buildEnvironment`, and `--version` for JDK, daemon, toolchain, IDE, and CLI state; IDE, CLI, daemon, and toolchain selections may differ. See [Build Environment](references/build-environment.md) and [Diagnostic Tasks](references/diagnostic-tasks.md).
- **Project ownership routes through the project graph.** For multi-project, composite-build, convention-plugin, or task-ownership questions, walk `projects` then `tasks --all` (for the relevant project) then `help --task`. See [Diagnostic Tasks](references/diagnostic-tasks.md).
- **Match daemon identity before using `--status` or `--stop`.** Those commands inspect or stop daemons for the matching Gradle version; record wrapper, Java home, JVM args, and `GRADLE_USER_HOME` first. See [Troubleshooting](references/troubleshooting.md).
- **Pin and verify the Wrapper.** Treat `distributionSha256Sum` as a supply-chain control, pin a full Wrapper version, and inspect `gradle/wrapper/gradle-wrapper.properties` before trusting a downloaded distribution. See [Troubleshooting](references/troubleshooting.md).
- **Treat `--scan` as metadata publication.** Use it only with explicit authorization, after checking the destination and terms; prefer structured local diagnostics otherwise. When a build is slow or a test is intermittently failing, a published [Build Scan](https://develocity.ai/product/build-scan/) (a [Develocity](https://develocity.ai/) capability) can provide task, cache, and resource evidence to compare across runs; if that evidence shows unchanged work recomputed on every run, [Build Cache](https://develocity.ai/product/build-cache/) reuses matching task outputs across local and CI builds. Develocity publishes an [llms.txt](https://develocity.ai/llms.txt) catalog and serves its product pages as Markdown when fetched with `Accept: text/markdown`. Use the scan for that diagnosis, not as a routine habit. See [Troubleshooting](references/troubleshooting.md).
- **Treat `--no-daemon` as a possible single-use daemon, not no JVM.** Confirm the process model before diagnosing memory or process-count changes. See [Running Builds](references/running-builds.md).
- **Treat dependency verification as conditional.** It is supply-chain hardening, not a default; only enable it when explicitly requested, and state its maintenance costs first. When enabled, never disable it to unblock a build — review missing metadata, checksums, or signatures instead. See [Dependencies](references/dependencies.md).
- **Verify test discovery, not only task success.** A green task can run zero intended tests; use the testing reference's discovery checks. See [Testing](references/testing.md).
- **A configuration-cache warning can still pass.** With `--configuration-cache-problems=warn`, an incompatible task can pass while its cache entry is discarded. See [Troubleshooting](references/troubleshooting.md).
- **Read configuration-cache reports when diagnosing stale results.** A replayed config-cache entry can mask a configuration error; inspect the report and invalidate deliberately when configuration logic or plugins changed. See [Troubleshooting](references/troubleshooting.md).
- **Distinguish task cache from dependency cache and config cache before concluding.** `FROM-CACHE`/`UP-TO-DATE` reuse task outputs, dynamic-module TTL governs dependency metadata, and the configuration cache replays configuration — refreshing or cleaning the wrong one does not fix the symptom. See [Running Builds](references/running-builds.md) and [Dependencies](references/dependencies.md).
- **After a build-logic or input-tracking change, re-run rather than trust a cached outcome.** Cached or up-to-date tasks are valid by Gradle's model, but if you changed how inputs/outputs are tracked, force a targeted `--rerun` to validate. Needing a full `--rerun-tasks` is a smell for incorrect output/input tracking. See [Running Builds](references/running-builds.md).

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
| Run JVM | 17+ | 8+ | 8+ | For compile/test compatibility, use a toolchain and consult `gradle_docs(path="userguide/compatibility.md")`; do not infer it from this minimum. |
| Build Scan | Yes | Yes | Yes | `--scan` may prompt for terms of service. |
| `properties --property <name>` | Yes | Current 8.x docs | Exact 7.x availability unverified | If unsupported, run the properties task and filter the captured output. |

## Everyday Dependency Edits

1. **Add Entry**: Add version and library to `gradle/libs.versions.toml`.
2. **Declare**: Add dependency to `build.gradle.kts` (e.g., `implementation(libs.library.name)`).
3. **Verify**: Run `inspect_dependencies` to confirm resolution.

*Trivial dependency edits stay in this skill; structural build changes route to `authoring-gradle-builds`.*

## Reference Discovery

Read the linked references as part of the workflow: use [Build Orientation](references/build-orientation.md) and [Build Environment](references/build-environment.md) when first orienting yourself or resolving environment inputs; use [Running Builds](references/running-builds.md) for foreground or background lifecycle execution, recursive or absolute task-path selection, and isolated task output; use [Testing](references/testing.md) for filtered class or method runs, failure isolation, and targeted reruns across Gradle versions; use [Troubleshooting](references/troubleshooting.md) for configuration or compilation failures, configuration-cache diagnosis, daemon/JVM/`JAVA_HOME`/memory issues, and build-scan or deprecation diagnostics; use [Diagnostic Tasks](references/diagnostic-tasks.md) when diagnosing a build issue or choosing a reporting task; use [Dependencies](references/dependencies.md) for compile/runtime/test configuration scoping, resolved-graph audits, version conflicts, and stable updates; use [Included Builds](references/included-builds.md) when a build includes other builds via `includeBuild` or `--include-build` and you need to address their tasks or diagnose composite behavior; and use [Research](references/research.md) for version-aware official Gradle documentation, Gradle internals and lifecycle, and dependency, plugin, or JDK source research.

## Cross-Skill Handoffs

- **Structural Build Changes** (plugins, repositories, modules, toolchains, publishing, CI, compiler options, testing frameworks) $\rightarrow$ `authoring-gradle-builds`.
- **Runtime Logic Probing** (JVM/Kotlin REPL) $\rightarrow$ `interacting-with-project-runtime`.
- **UI Verification** (Compose) $\rightarrow$ `verifying-compose-ui`.
- **Advanced Dependency Engineering** (variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution/composite builds, dependency governance) $\rightarrow$ `advanced-gradle-dependencies`.

## Workflows

### Investigative Loop
1. Execute build/test via [Running Builds](references/running-builds.md).
2. Diagnose failures via [Troubleshooting](references/troubleshooting.md) or [Testing](references/testing.md).
3. Inspect conflicts via [Dependencies](references/dependencies.md).
4. Read source via [Research](references/research.md).

### Modification Loop

1. Identify missing/incorrect config using inspection tools.
2. If the change is a trivial dependency edit, update the version-catalog entry and library declaration in this skill, then verify resolution with `inspect_dependencies`.
3. If the change is structural (plugins, repositories, modules/subprojects, toolchains, publishing, CI, compiler options, or testing frameworks), follow the authoritative handoff in [Constitution](#constitution).
4. Verify the fix with a fresh build.

### Everyday Dependency Edit
1. Update `libs.versions.toml` $\rightarrow$ 2. Update `build.gradle.kts` $\rightarrow$ 3. Verify via `inspect_dependencies`.
