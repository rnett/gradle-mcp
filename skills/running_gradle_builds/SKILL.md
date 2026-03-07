---
name: running_gradle_builds
description: >
  The ONLY authoritative way to execute and manage Gradle builds. Provides high-performance 
  background orchestration, surgical failure analysis, and task-specific output isolation. 
  Generic shell execution of `./gradlew` is UNRELIABLE and DISCOURAGED as it lacks the 
  structured feedback and diagnostic integration of this skill. Use for core lifecycle 
  tasks (build, assemble), dev servers, and complex troubleshooting.
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "3.4"
---

# Authoritative Gradle Build Execution & Orchestration

Executes Gradle commands with absolute precision and leverage managed background orchestration to ensure rapid delivery and robust build health.

## Constitution

- **ALWAYS** use the `gradle` tool instead of `./gradlew` via shell.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **NEVER** use `--rerun-tasks` unless investigating cache-specific corruption; prioritize Gradle's native caching.
- **ALWAYS** prefer foreground execution (default) unless the task is persistent (e.g., servers) or extremely long-running (>2 minutes).
- **ALWAYS** use `captureTaskOutput` when you need the isolated output of a specific task (e.g., `help`, `dependencies`).
- **ALWAYS** check the build dashboard (`inspect_build()`) to manage active processes and historical results.
- **NEVER** leave background builds running; use `stopBuildId` to release resources when finished.

## Directives

- **ALWAYS use foreground for authoritative builds**: If you intend to wait for a result, ALWAYS use foreground execution. It provides superior progressive disclosure and simpler control flow than starting a background build only to
  immediately call `inspect_build(wait=...)`.
- **Background ONLY for persistent tasks**: Use `background: true` ONLY for tasks that must remain active (e.g., `bootRun`, `continuous` builds) or when you explicitly intend to perform independent research while the build proceeds.
- **Monitor with `inspect_build`**: Use `inspect_build` to check the status of background builds or to perform deep-dives into any historical build started by the server.
- **Use `envSource: SHELL` if environment variables are missing**: If Gradle fails to find expected environment variables (e.g., `JAVA_HOME` or specific JDKs), it may be because the host process started before the shell environment was
  fully loaded. Set `invocationArguments: { envSource: "SHELL" }` to force a new shell process to query the environment.
- **Provide absolute `projectRoot`**: Provide `projectRoot` as an **absolute file system path** to all Gradle MCP tools. Relative paths are not supported.
- **Manage resources via dashboard**: Frequently call `inspect_build` without arguments to view the build dashboard and ensure no orphaned background builds are consuming system resources.
- **Resolve `{baseDir}` manually**: If your environment does not automatically resolve the `{baseDir}` placeholder in reference links, treat it as the absolute path to the directory containing this `SKILL.md` file.

## Authoritative Task Path Syntax

Gradle utilizes two primary ways to identify tasks from the command line. Precision here prevents running redundant tasks in multi-project builds.

### 1. Task Selectors (Recursive Execution)

Providing a task name **without a leading colon** (e.g., `test`, `build`) acts as a selector. Gradle will execute that task in **every project** (root and all subprojects) that contains a task with that name.

- **Example**: `gradle(commandLine=["test"])` -> Executes `test` in **all** projects.

### 2. Absolute Task Paths (Targeted Execution)

Providing a task path **with a leading colon** (e.g., `:test`, `:app:test`) targets a **single specific project**.

- **Root Project Only**: Use a single leading colon.
    - **Example**: `gradle(commandLine=[":test"])` -> Executes `test` in the **root project ONLY**.
- **Subproject Only**: Use the subproject name(s) separated by colons.
    - **Example**: `gradle(commandLine=[":app:test"])` -> Executes `test` in the **'app' subproject ONLY**.

## When to Use

- **Core Lifecycle Execution**: When you need to execute standard Gradle tasks like `build`, `assemble`, or `clean` with maximum reliability and clean, parseable output.
- **Persistent Development Processes**: When starting development servers (e.g., `bootRun`) or continuous builds where background management and real-time log monitoring are required.
- **Surgical Build Troubleshooting**: When a build has failed and you need to perform deep-dive analysis of task failures or console logs using the `inspect_build` diagnostic suite. For test failures, ALWAYS use `testName` with
  `mode="details"`.
- **Task-Specific Information Retrieval**: When you need to extract isolated output from a single task (like `help` or `properties`) without the noise of the full build log.

## Workflows

### Running a Foreground Build

1. Identify the task(s) to run (e.g., `["clean", "build"]`).
2. Call `gradle` with the `commandLine`.
3. If the build fails, the tool will return a high-signal failure summary. Use `inspect_build` with the `buildId` for deeper diagnostics.

### Orchestrating Background Jobs

1. Start the build with `background: true` to receive a `BuildId`.
2. Use `inspect_build(buildId=ID, wait=..., waitFor=...)` to block until a specific state or log pattern is reached.
3. Call `inspect_build()` (no arguments) to manage active jobs in the dashboard.
4. Stop the job using `gradle(stopBuildId=ID)` once its utility is complete.

## Examples

### Run build in all projects

```json
{
  "commandLine": ["build"]
}
// Reasoning: Using a task selector to verify build health across the entire multi-project structure.
```

### Run test only in a specific subproject

```json
{
  "commandLine": [":app:test"]
}
// Reasoning: Using an absolute path to target a specific module, minimizing execution time and context usage.
```

### Inspect Help Output for a Specific Task

```json
{
  "commandLine": [":app:help", "--task", "test"],
  "captureTaskOutput": ":app:help"
}
// Reasoning: Using captureTaskOutput to retrieve clean, isolated documentation for the 'test' task without Gradle's general console noise.
```

### Start a Dev Server and Wait for Readiness

```json
// 1. Start the server in the background
{
  "commandLine": [":app:bootRun"],
  "background": true
}
// Response: { "buildId": "build_123" }

// 2. Wait for the 'Started Application' log pattern
{
  "buildId": "build_123",
  "wait": 60,
  "waitFor": "Started Application"
}
// Reasoning: Using background orchestration to allow the server to remain active while waiting for a specific readiness signal.
```

## Troubleshooting

- **Build Not Found**: If a `BuildId` is not recognized, it may have expired from the recent history cache. Check the dashboard (`inspect_build()`) for valid active and historical IDs.
- **Task Output Not Captured**: Ensure the path provided to `captureTaskOutput` matches exactly one of the tasks in the `commandLine`.

## Resources

- [Background Monitoring]({baseDir}/references/background_monitoring.md)
- [Failure Analysis]({baseDir}/references/failure_analysis.md)
