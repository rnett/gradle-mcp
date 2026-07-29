---
name: gradle
description: |
  Provides authoritative guidance for ALL Gradle operations: executing builds, running tests with surgical filtering, and diagnosing failures;
  ALWAYS use instead of raw shell `./gradlew` for build execution, test runs, task introspection, and documentation research.

  ## Positive Triggers (when to activate)
  - User asks to run a Gradle build, test, or any lifecycle task
  - User asks to inspect project structure, task graphs, or properties
  - User asks to investigate test failures, build errors, or task problems
  - User asks about Gradle task syntax, test selectors, or execution modes
  - User needs background job management or build progress monitoring
  - User wants to look up official Gradle documentation or DSL reference

  ## Negative Triggers (when NOT to activate)
  - User needs to create or modify build.gradle(.kts)/settings.gradle(.kts), add/remove dependencies, or create modules (route to gradle-build-authoring)
  - User needs performance auditing or build script refactoring (route to gradle-build-authoring)
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "5.0"
---

# Authoritative Gradle Build Execution, Testing & Project Introspection

Executes builds, runs tests with high-precision filtering, introspects project structure, and diagnoses failures using managed orchestration and structured diagnostics.

## Constitution

- **ALWAYS** use the `gradle` tool instead of `./gradlew` via shell.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **ALWAYS** prefer foreground execution (default) unless the task is persistent (e.g., servers) or extremely long-running (>2 minutes), or you explicitly intend to perform independent research while it proceeds.
- **ALWAYS** use `captureTaskOutput` when you need the isolated output of a specific task (e.g., `help`, `projects`, `tasks`, `properties`, `dependencies`).
- **STRONGLY PREFERRED**: Use `query_build` for all diagnostics. It is more token-efficient than reading raw console logs and provides structured access to failures, problems, and per-test output.
- **ALWAYS** use `query_build` with `kind="TESTS"` and `query="FullTestName"` to access full test output and stack traces.
- **NEVER** use `taskPath` or `captureTaskOutput` to investigate specific test failures; these provide the overall task log which is often truncated and lacks per-test isolation. Per-test output (via `query`) is authoritative and includes
  full stack traces.
- **NEVER** use `--rerun-tasks` unless investigating project-wide cache-specific corruption; prefer `--rerun` for individual tasks.
- **NEVER** guess task names or options; use the `help --task <name>` command for authoritative documentation.
- **NEVER** leave background builds running; use `stopBuildId` to release resources when finished.
- **ALWAYS** use `gradle_docs` for authoritative documentation lookup instead of generic web searches.

- **ALWAYS** use `:properties --property <name>` for surgical property extraction.

## Build Authoring (Cross-Reference)

For build script authoring, module creation, performance optimization, build logic refactoring, and dependency management, use the **[gradle-build-authoring](../gradle-build-authoring/SKILL.md)** skill.

## Directives

### Authoritative Task Path Syntax

Gradle uses two ways to identify tasks from the command line. Precision prevents running redundant tasks in multi-project builds.

#### Task Selectors (Recursive Execution)

Providing a task name **without a leading colon** (e.g., `test`, `build`) acts as a selector. Gradle executes that task in **every project** (root and all subprojects) that contains a task with that name.

- **Example**: `gradle(commandLine=["test"])` -> Executes `test` in **all** projects.

#### Absolute Task Paths (Targeted Execution)

Providing a task path **with a leading colon** (e.g., `:test`, `:app:test`) targets a **single specific project**.

- **Root Project Only**: Use a single leading colon. `gradle(commandLine=[":test"])` -> Root project ONLY.
- **Subproject Only**: Use the subproject name(s) separated by colons. `gradle(commandLine=[":app:test"])` -> ':app' subproject ONLY.

### Authoritative Test Selection (`--tests`)

The `--tests` flag supports powerful, high-precision filtering:

- **Exact Class**: `--tests com.example.MyTest`
- **Exact Method**: `--tests com.example.MyTest.myTestMethod`
- **Wildcard Method**: `--tests com.example.MyTest.test*` (All methods starting with 'test')
- **Package Filter**: `--tests com.example.service.*` (All tests in the 'service' package)
- **Class Prefix**: `--tests *IntegrationTest` (All classes ending in 'IntegrationTest')
- **Character Wildcard**: `--tests com.example.Test?` (Matches Test1, TestA, etc.)
- **Multi-Filter**: `gradle(commandLine=["test", "--tests", "ClassA", "--tests", "ClassB"])`

Patterns match against the **fully qualified name** of the test class or method.

### Foreground vs. Background Execution

- **ALWAYS use foreground for authoritative runs**: If you intend to wait for a result, ALWAYS use foreground execution. It provides superior progressive disclosure and simpler control flow.
- **Background ONLY for persistent tasks**: Use `background: true` ONLY for tasks that must remain active (e.g., `bootRun`, continuous builds) or when you intentionally intend to perform independent research while the build proceeds.
- **Foreground is safe**: Do not fear running high-output suites in the foreground. The `gradle` tool uses progressive disclosure to provide concise summaries and structured results, keeping session history clean.

### `captureTaskOutput` Usage

Use `captureTaskOutput` when you need clean, isolated output from a specific task without Gradle's general console noise. This is ideal for introspection tasks:

- `captureTaskOutput: ":projects"` - Clean project list
- `captureTaskOutput: ":app:tasks"` - Task list for a specific project
- `captureTaskOutput: ":help"` - Documentation for a specific task
- `captureTaskOutput: ":properties"` - Single property extraction
- `captureTaskOutput: ":app:dependencyInsight"` - Dependency resolution path

### `gradle_docs` Tag Syntax

Use `gradle_docs` for authoritative documentation. Always scope with tags:

| Tag                  | Section                                            |
|----------------------|----------------------------------------------------|
| `tag:userguide`      | Official Gradle User Guide                         |
| `tag:dsl`            | Gradle DSL Reference (Groovy and Kotlin DSL)       |
| `tag:javadoc`        | Gradle Java API Reference                          |
| `tag:samples`        | Official Gradle samples and examples               |
| `tag:release-notes`  | Version-specific release insights                  |
| `tag:best-practices` | Official best practices and performance guidelines |

Explore sections with `path="."`. Search scoped with `tag:<section> <term>`.

### Resource Management

- Use `query_build()` without arguments to view the build dashboard and ensure no orphaned background builds are consuming system resources.
- Set `invocationArguments: { envSource: "SHELL" }` if Gradle cannot find expected env vars (e.g., `JAVA_HOME`).

### Diagnostic Inspection (See References)

For comprehensive guidance on using `query_build` and `wait_build` for diagnostics, including JSON examples for every inspection mode (DASHBOARD, SUMMARY, FAILURES, PROBLEMS, TASKS, TESTS, CONSOLE, PROGRESS), refer
to: [query_build Diagnostics Reference](references/query_build_diagnostics.md).

## Workflows

### Running a Foreground Build

1. Identify the task(s) to run (e.g., `["clean", "build"]`).
2. Call `gradle(commandLine=["...", "..."])`.
3. If the build fails, the tool returns a high-signal failure summary. Use `query_build` with the `buildId` for deeper diagnostics via [query_build Diagnostics Reference](references/query_build_diagnostics.md).

### Running Specific Tests

1. Identify the project path (e.g., `:app`) and the test filter (e.g., `com.example.MyTestClass*`).
2. Call `gradle(commandLine=[":app:test", "--tests", "com.example.MyTest"])`.
3. If failures are reported, use `query_build` to get detailed test output.

### Orchestrating Background Jobs

1. Start the build with `background: true` to receive a `BuildId`.
2. Use `wait_build(buildId=ID, timeout=..., waitFor=...)` to block until a specific state or log pattern is reached.
3. Use `query_build()` (no arguments) to manage active jobs in the dashboard.
4. Stop the job using `gradle(stopBuildId=ID)` when finished.

### Introspecting Project Structure

1. Run `gradle(commandLine=[":projects"], captureTaskOutput=":projects")` to map the multi-project hierarchy.
2. Run `gradle(commandLine=[":app:tasks", "--all"], captureTaskOutput=":app:tasks")` to discover runnable tasks.
3. Run `gradle(commandLine=[":help", "--task", "test"], captureTaskOutput=":help")` for task-specific documentation.
4. Run `gradle(commandLine=[":properties", "--property", "version"], captureTaskOutput=":properties")` for surgical property extraction.
5. For detailed dependency resolution paths: `gradle(commandLine=[":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"], captureTaskOutput=":app:dependencyInsight")`.

### Documentation Research

1. Search the user guide: `gradle_docs(query="tag:userguide <term>", projectRoot="/path/to/project")`.
2. Navigate the DSL reference: `gradle_docs(path="dsl/org.gradle.api.Project.html", projectRoot="/path/to/project")`.
3. Check for breaking changes: `gradle_docs(query="tag:release-notes", version="8.6")`.
4. Search for samples: `gradle_docs(query="tag:samples toolchains", projectRoot="/path/to/project")`.
5. Search javadocs: `gradle_docs(query="tag:javadoc Project", projectRoot="/path/to/project")`.

### Investigating Test Failures

1. Identify the `BuildId` from the build result.
2. Use `query_build(buildId=ID, kind="TESTS", outcome="FAILED")` to list all failed tests.
3. Use `query_build(buildId=ID, kind="TESTS", query=TNAME)` to see the full output and stack trace for a specific test.
4. **DO NOT** use `taskPath` or `captureTaskOutput` for test failure investigation.

## When to Use

- **Core Lifecycle Execution**: When you need to execute standard Gradle tasks (`build`, `assemble`, `clean`) with reliable, parseable output.
- **Test Execution & Diagnostics**: When running tests with `--tests` filtering, isolating failures, or retrieving full stack traces.
- **Introspection & Mapping**: When mapping multi-module project hierarchies, discovering runnable tasks, or auditing build configuration.
- **Surgical Property Inspection**: When extracting a specific property value (artifact version, build directory) for use in a subsequent task.
- **Persistent Development Processes**: When starting dev servers (`bootRun`) or continuous builds where background management is required.
- **Task-Specific Information Retrieval**: When you need isolated output from a single task (`help`, `projects`, `tasks`) without build noise.
- **Build Failure Diagnostics**: When performing deep-dive analysis of task failures, problems, or compilation errors.
- **Documentation & DSL Research**: When looking up official Gradle syntax, user guide topics, or release notes.
- **Build Script Authoring**: When writing or modifying `build.gradle.kts`, `settings.gradle.kts`, creating modules, or optimizing build performance — use `gradle-build-authoring` instead.

## Examples

### Run build in all projects

Tool: `gradle`

```json
{
  "commandLine": ["build"]
}
// Reasoning: Task selector (no colon) verifies build health across the entire multi-project structure.
```

### Run a single test class in a specific subproject

Tool: `gradle`

```json
{
  "commandLine": [":app:test", "--tests", "com.example.service.MyServiceTest"]
}
// Reasoning: Absolute task path with exact class filter for the fastest possible feedback loop.
```

### Inspect help output for a specific task

Tool: `gradle`

```json
{
  "commandLine": [":app:help", "--task", "test"],
  "captureTaskOutput": ":app:help"
}
// Reasoning: Using captureTaskOutput to retrieve clean, isolated documentation.
```

### List all sub-projects in the build

Tool: `gradle`

```json
{
  "commandLine": [":projects"],
  "captureTaskOutput": ":projects"
}
// Reasoning: Using captureTaskOutput to retrieve the project hierarchy list without startup noise.
```

### Surgically inspect the 'version' property

Tool: `gradle`

```json
{
  "commandLine": [":properties", "--property", "version"],
  "captureTaskOutput": ":properties"
}
// Reasoning: Using --property to isolate a single value and avoid retrieving thousands of unrelated properties.
```

### Analyze a specific dependency conflict

Tool: `gradle`

```json
{
  "commandLine": [
    ":app:dependencyInsight",
    "--dependency",
    "com.google.guava:guava",
    "--configuration",
    "runtimeClasspath"
  ],
  "captureTaskOutput": ":app:dependencyInsight"
}
// Reasoning: Using dependencyInsight to isolate the resolution path for a specific artifact.
```

### Start a dev server and wait for readiness

Tool: `gradle`

```json
// Step 1: Start the server in the background
{
  "commandLine": [":app:bootRun"],
  "background": true
}
// Response: { "buildId": "build_123" }

// Step 2: Wait for readiness signal
{
  "buildId": "build_123",
  "timeout": 60,
  "waitFor": "Started Application"
}
// Reasoning: Background orchestration allows the server to remain active while waiting for readiness.
```

### Search official Gradle documentation

Tool: `gradle_docs`

```json
{
  "query": "tag:dsl signing plugin",
  "projectRoot": "/absolute/path/to/project"
}
// Reasoning: Using the DSL tag to find authoritative syntax for the signing plugin configuration.
```

### List all failed tests in a build

Tool: `query_build`

```json
{
  "buildId": "build_abc123",
  "kind": "TESTS",
  "outcome": "FAILED"
}
// Reasoning: Isolating only the failures from a large test suite for efficient triage.
```

## Troubleshooting

- **Build Not Found**: If a `BuildId` is not recognized, it may have expired from the recent history cache. Check the dashboard (`query_build()`) for valid active and historical IDs.
- **Task Output Not Captured**: Ensure the path provided to `captureTaskOutput` matches exactly one of the tasks in the `commandLine`.
- **Missing environment variables**: Set `invocationArguments: { envSource: "SHELL" }` if Gradle cannot find expected env vars (e.g., `JAVA_HOME`).

## Resources

- [query_build Diagnostics Reference](references/query_build_diagnostics.md) — Complete diagnostic patterns for DASHBOARD, SUMMARY, FAILURES, PROBLEMS, TASKS, TESTS, CONSOLE, and PROGRESS.
- [Background Monitoring Patterns](references/background_monitoring.md)
- [Authoritative Diagnostic Tasks](references/diagnostic_tasks.md) — Built-in introspection tasks.
- [Official Gradle Documentation Research](references/gradle_docs_research.md) — Guidance on using `gradle_docs` for authoritative documentation.
