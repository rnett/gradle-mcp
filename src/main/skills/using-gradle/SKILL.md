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

  ## Negative Triggers (when NOT to activate)
  - Modifying build scripts, settings, or module definitions (use `authoring-gradle-builds`).
  - Adding plugins, repositories, or dependency declarations (use `authoring-gradle-builds`).
  - Configuring toolchains, compiler options, or testing frameworks (use `authoring-gradle-builds`).
  - Executing arbitrary Kotlin/Java code via the REPL (use `interacting-with-project-runtime`).
  - Rendering Compose UI components (use `verifying-compose-ui`).
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.0.0"
---
<!--
class: authored-local
skill: using-gradle
-->

# Authoritative Gradle Build Execution, Testing & Inspection

Inspects, executes, diagnoses, and research existing Gradle builds using managed orchestration and structured diagnostics.

## Constitution

- **ALWAYS** use the `gradle` tool instead of `./gradlew` via shell.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **ALWAYS** prefer foreground execution unless the task is persistent (servers) or extremely long-running (>2 minutes).
- **STRONGLY PREFERRED**: Use `query_build` for all diagnostics — more token-efficient than raw console logs.
- **ALWAYS** use `query_build(kind="TESTS", query="FullTestName")` for test output and stack traces.
- **NEVER** use `taskPath` or `captureTaskOutput` to investigate specific test failures.
- **NEVER** use `--rerun-tasks` unless investigating project-wide cache corruption; prefer `--rerun` for individual tasks.
- **NEVER** guess task names; use `help --task <name>` for authoritative documentation.
- **NEVER** leave background builds running; use `stopBuildId` to release resources.
- **ALWAYS** use `gradle_docs` for authoritative documentation instead of generic web searches.

## Decision Routing

| Need | Reference | Load When |
|------|-----------|-----------|
| Map project hierarchy, discover tasks, inspect properties | [Project Structure](references/project-structure.md) | Introspecting a new or unfamiliar project |
| Execute builds, manage background jobs | [Running Builds](references/running-builds.md) | Starting any build execution |
| Diagnose failures, use diagnostic tasks | [Build Diagnostics](references/build-diagnostics.md) | Build fails or produces problems |
| Run and investigate tests | [Test Diagnostics](references/test-diagnostics.md) | Running tests or investigating failures |
| Research Gradle docs, internals, release notes | [Gradle Internals](references/gradle-internals.md) | Looking up official docs or internal APIs |
| Audit dependency graph, resolve conflicts | [Dependency Inspection](references/dependency-inspection.md) | Checking dependencies or version conflicts |
| Discover library updates | [Dependency Updates](references/dependency-updates.md) | Checking for newer dependency versions |
| Search and read dependency/plugin sources | [Dependency Sources](references/dependency-sources.md) | Reading source code of dependencies |

## Cross-Skill Handoffs

- **Build definition changes** (scripts, settings, modules, dependencies, toolchains) → Load `authoring-gradle-builds`.
- **Runtime code probing** (JVM/Kotlin REPL) → Load `interacting-with-project-runtime`.
- **Visual verification** (Compose UI) → Load `verifying-compose-ui`.

## Workflows

### Investigative Loop

1. Execute the build or test using [Running Builds](references/running-builds.md).
2. If failures occur, diagnose with [Build Diagnostics](references/build-diagnostics.md) or [Test Diagnostics](references/test-diagnostics.md).
3. If the root cause involves a dependency conflict, inspect with [Dependency Inspection](references/dependency-inspection.md).
4. If you need to read the source of a conflicting library, use [Dependency Sources](references/dependency-sources.md).

### Modification Loop

1. Identify the missing or incorrect dependency/configuration using this skill's inspection tools.
2. Hand off to `authoring-gradle-builds` to make the build definition change.
3. Return here to verify the fix with a fresh build.

## Task Path Syntax

Gradle uses two ways to identify tasks:

- **Task Selectors** (no colon): `gradle(commandLine=["test"])` → runs `test` in **all** projects.
- **Absolute Task Paths** (with colon): `gradle(commandLine=[":app:test"])` → runs only in `:app`.

## Test Selection (`--tests`)

- **Exact Class**: `--tests com.example.MyTest`
- **Exact Method**: `--tests com.example.MyTest.myTestMethod`
- **Wildcard**: `--tests com.example.MyTest.test*`
- **Package**: `--tests com.example.service.*`
- **Suffix**: `--tests *IntegrationTest`

## `captureTaskOutput` Usage

Use for clean, isolated output from introspection tasks:
- `":projects"` — Clean project list
- `":app:tasks"` — Task list for a specific project
- `":help"` — Documentation for a specific task
- `":properties"` — Property extraction

## Resource Management

- Use `query_build()` without arguments to view the build dashboard and check for orphaned background builds.
- Set `invocationArguments: { envSource: "SHELL" }` if Gradle cannot find expected env vars (e.g., `JAVA_HOME`).
