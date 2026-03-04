---
name: gradle-build
description: Execute any Gradle task with robust background management and integrated failure analysis. Use for common build tasks like 'build', 'assemble', or starting development servers.
license: Apache-2.0
allowed-tools: gradlew inspect_build
metadata:
  author: rnett
  version: "2.2"
---

# High-Performance Gradle Build Execution & Management

Execute and manage Gradle commands with ease, whether in the foreground or as persistent background jobs. Leverage deep diagnostic tools to rapidly resolve build failures.

## Directives

- **ONLY use MCP tools**: NEVER use `./gradlew` via a shell unless you have exhausted all attempts to use the `gradlew` tool and it repeatedly fails to meet your requirements. Falling back to the shell is a **last resort** for edge cases
  where the Tooling API or the server's output capturing is demonstrably insufficient.
- **Background for long tasks**: ALWAYS set `background: true` in `gradlew` for tasks that take a long time or are persistent (e.g. `bootRun`, `continuous` builds, long-running test suites). This allows you to continue working while
  monitoring the build's progress.
- **Use `captureTaskOutput` for clean results**: When running tasks where you need specific, clean output (e.g., `dependencies`, `help`, `properties`), set `captureTaskOutput` in `gradlew` to the task path. This avoids noise and makes the
  output much easier to parse.
- **Monitor with `inspect_build`**: Use the `inspect_build` tool to check the status of background builds or to get detailed information about any build started by the server.
- **Provide absolute `projectRoot` when in doubt**: Provide `projectRoot` as an **absolute file system path** to any Gradle MCP tool that supports it unless you are certain it is not required. The project root is the directory containing
  the `gradlew` script and `settings.gradle(.kts)` file. **Relative paths are not supported.**
- **Task Path Syntax**: When specifying tasks, understand the distinction:
    - `:task` (starts with colon): Targets the task in the **root project only**.
    - `task` (no leading colon): Targets the task in **all projects** (root and all subprojects).
    - `:app:task`: Targets the task in the `app` subproject.
- **Check the dashboard frequently**: Call `inspect_build` without arguments to see the build dashboard (active and recent builds).
- **Progressive Disclosure**: For complex failure analysis or background monitoring patterns, refer to the documents in `references/`.

## When to Use

- When you need to execute any Gradle task (`build`, `assemble`, `clean`, etc.).
- When you need to start a development server or a continuous build process.
- When you need to monitor the status of a background job.
- When a Gradle build has failed and you need to diagnose the cause.

## Workflows

### Running a Foreground Command

1. Use `gradlew` with the `commandLine` as an array of strings (e.g. `["clean", "build"]`).
2. If you only need output from a single task (e.g. `:app:help`), set `captureTaskOutput: ":app:help"`.
3. If the build fails, use `inspect_build` with the returned `buildId` to investigate.

### Running and Managing Background Jobs

1. Use `gradlew` with `background: true`. This returns a `buildId`.
2. Use `inspect_build(buildId=ID, wait=..., waitFor=...)` to monitor progress.
3. Use `inspect_build()` (no arguments) to see all active background jobs in the dashboard.
4. Use `gradlew(stopBuildId=ID)` when the job is no longer needed.
5. See [Background Monitoring](references/background-monitoring.md) for advanced patterns.

### Investigating Build Failures

1. Identify the `buildId` from the execution result or the dashboard.
2. Use `inspect_build(buildId=ID, include=["failures", "problems"])` to get structured diagnostic information.
3. If needed, use `include=["console"]` with `consoleOptions` to see specific parts of the log.
4. See [Failure Analysis](references/failure-analysis.md) for a deep dive into diagnosing complex issues.

## Examples

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
