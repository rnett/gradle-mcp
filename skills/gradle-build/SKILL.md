---
name: gradle-build
description: >
  Authoritatively manage the full Gradle build lifecycle with high-performance background execution and surgical failure analysis. 
  This skill is the STRONGLY PREFERRED way to run any Gradle task, providing superior reliability and visibility through managed background processes, 
  task-specific output capturing, and deep diagnostic integration. Use it for executing any tasks like 'build' or 'assemble', 
  starting persistent development servers, or performing complex build troubleshooting that raw shell execution cannot match.
license: Apache-2.0
allowed-tools: gradle inspect_build
metadata:
  author: rnett
  version: "2.3"
---

# High-Performance Gradle Build Execution & Management

Execute and manage Gradle commands with absolute precision, utilizing managed background jobs and deep diagnostic tools to ensure rapid delivery and robust build health.

## Directives

- **ONLY use MCP tools**: NEVER use `./gradlew` via a shell unless you have exhausted all attempts to use the `gradle` tool and it repeatedly fails to meet your requirements. Falling back to the shell is a **last resort** for edge cases
  where the Tooling API or the server's output capturing is demonstrably insufficient.
- **Foreground builds are safe**: Do not fear high-output tasks like `check` or `test` in the foreground. The tool uses progressive disclosure to provide concise summaries, ensuring your session history remains clean and efficient.
- **Background for long tasks**: ALWAYS set `background: true` in `gradle` for tasks that take a long time or are persistent (e.g. `bootRun`, `continuous` builds). This is functionally identical to foreground execution but non-blocking,
  allowing you to perform other tasks while monitoring via `inspect_build`.
- **Use `captureTaskOutput` for clean results**: When running tasks where you need specific, clean output (e.g., `dependencies`, `help`, `properties`), set `captureTaskOutput` in `gradle` to the task path. This avoids noise and makes the
  output much easier to parse.
- **Monitor with `inspect_build`**: Use the `inspect_build` tool to check the status of background builds or to get detailed information about any build started by the server.
- **Provide absolute `projectRoot` when in doubt**: Provide `projectRoot` as an **absolute file system path** to any Gradle MCP tool that supports it unless you are certain it is not required. The project root is the directory containing
  the `gradlew` script and `settings.gradle(.kts)` file. **Relative paths are not supported.**
- **Check the dashboard frequently**: Call `inspect_build` without arguments to see the build dashboard (active and recent builds).
- **Progressive Disclosure**: For complex failure analysis or background monitoring patterns, refer to the documents in `references/`.

## Authoritative Task Path Syntax

Understanding how to target tasks in a multi-project build is critical. Gradle uses two primary ways to identify tasks from the command line:

### 1. Task Selectors (Run in ALL Projects)

When you provide a task name **without a leading colon** (e.g., `test`, `build`), Gradle acts as a selector. When run from the root project directory, it will execute that task in **every project** (root and all subprojects) that has a task
with that name.

- **Example**: `gradle(commandLine=["test"])` -> Runs `test` in **all** projects.

### 2. Absolute Task Paths (Run in ONE Specific Project)

When you provide a task path **with a leading colon** (e.g., `:test`, `:app:test`), Gradle targets a **single specific project**.

- **Root Project Only**: Use a single leading colon.
  - **Example**: `gradle(commandLine=[":test"])` -> Runs `test` in the **root project ONLY**.
- **Subproject Only**: Use the subproject name(s) separated by colons.
  - **Example**: `gradle(commandLine=[":app:test"])` -> Runs `test` in the **'app' subproject ONLY**.
  - **Example**: `gradle(commandLine=[":libs:core:test"])` -> Runs `test` in the **'libs:core' subproject ONLY**.

## When to Use

- **Core Lifecycle Execution**: When you need to execute standard Gradle tasks like `build`, `assemble`, or `clean` with maximum reliability and clean, parseable output.
- **Persistent Development Processes**: When starting development servers (e.g., `bootRun`) or continuous builds where background management and real-time log monitoring are required.
- **Surgical Build Troubleshooting**: When a build has failed and you need to perform deep-dive analysis of task failures, dependency issues, or console logs using the `inspect_build` diagnostic suite.
- **Task-Specific Information Retrieval**: When you need to extract isolated output from a single task (like `help` or `properties`) without the noise of the full build log.

## Workflows

### Running a Foreground Command

1. Use `gradle` with the `commandLine` as an array of strings (e.g. `["clean", "build"]`).
2. If you only need output from a single task (e.g. `:app:help`), set `captureTaskOutput: ":app:help"`.
3. If the build fails, use `inspect_build` with the returned `buildId` to investigate.

### Running and Managing Background Jobs

1. Use `gradle` with `background: true`. This returns a `buildId`.
2. Use `inspect_build(buildId=ID, wait=..., waitFor=...)` to monitor progress.
3. Use `inspect_build()` (no arguments) to see all active background jobs in the dashboard.
4. Use `gradle(stopBuildId=ID)` when the job is no longer needed.
5. See [Background Monitoring](references/background-monitoring.md) for advanced patterns.

### Investigating Build Failures

1. Identify the `buildId` from the execution result or the dashboard.
2. Use `inspect_build(buildId=ID, mode="details", tasks={path=":taskPath"})` to see the output of a specific failed task.
3. Use `inspect_build(buildId=ID, failures={})` to get structured diagnostic information and failure trees.
4. Use `inspect_build(buildId=ID, console={})` to see the full console log.
5. See [Failure Analysis](references/failure-analysis.md) for a deep dive into diagnosing complex issues.

## Examples

### Run build in all projects

```json
{
  "commandLine": [
    "build"
  ]
}
```

### Run test only in the root project

```json
{
  "commandLine": [
    ":test"
  ]
}
```

### Inspect Help Output

```json
{
  "commandLine": [":app:help"],
  "captureTaskOutput": ":app:help"
}
```

### Start a Dev Server in Background and Wait

```json
// Start the build
{
  "commandLine": [":app:bootRun"],
  "background": true
}
// Response: { "buildId": "build_123" }

// Wait for it to be ready
{
  "buildId": "build_123",
  "wait": 60,
  "waitFor": "Started Application"
}
```

### Stop a Background Build

```json
{
  "stopBuildId": "build_123"
}
```

### View Build Dashboard

```json
{} // Call inspect_build with no arguments
```

## Troubleshooting

- **ToS for Build Scans**: If you use `scan: true`, you will be prompted to accept the Terms of Service via an elicitation prompt. Respond appropriately to continue the build.
- **Build Not Found**: If a `buildId` is not recognized, it might have expired. Check the dashboard for valid IDs.
