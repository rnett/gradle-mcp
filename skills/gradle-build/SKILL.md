---
name: gradle-build
description: Running Gradle commands in the foreground or background, managing long-running background jobs, and investigating build failures.
license: Apache-2.0
allowed-tools: run_gradle_command run_single_task_and_get_output background_run_gradle_command background_build_get_status background_build_list background_build_stop lookup_build lookup_build_tasks lookup_build_failures lookup_build_problems lookup_build_console_output lookup_latest_builds accept_scans_tos
metadata:
  author: rnett
  version: "1.1"
---

# Running Gradle Commands and Background Jobs

Instructions and examples for running Gradle commands, managing long-running jobs (like development servers), and investigating build failures.

## Directives

- **Prefer MCP tools over shell**: Always use the provided Gradle MCP tools instead of executing `./gradlew` via a shell or command line.
- **Identify project root**: If not specified, the project root is usually the current directory or the directory containing `settings.gradle(.kts)` or `gradlew` scripts.
- **Use `run_single_task_and_get_output` for inspection**: For tasks that only produce output (like `dependencies`, `properties`, `help`, `--help`, `--version`), use the specialized tool to get clean output without Gradle's boilerplate.
- **Background for long tasks**: Use background tools for tasks that take a long time or are persistent (e.g. `bootRun`, `run`, `continuous` builds).
- **Foreground for quick feedback**: Use foreground tools for quick tasks where immediate output is needed.
- **Check the console first**: When a build fails, review the console output provided by the command.
- **Use specialized lookup tools**: Don't rely solely on console output; use `lookup_build_failures` and `lookup_build_problems` for structured information.
- **Investigating previous builds**: You can investigate any build that was run via the MCP server at any point in the current session. Use `lookup_latest_builds` to find them.
- **Check recent history**: Use `lookup_latest_builds` to find the `BuildId` of any recent run, for builds that were ran via the Gradle MCP.

## When to Use

- When you need to execute any Gradle task (`build`, `assemble`, `clean`, etc.).
- When you need to inspect project configuration (`dependencies`, `properties`, `help`).
- When you need to view metadata about Gradle itself (`--help`, `--version`).
- When you need to start a development server or a continuous build process.
- When you need to monitor the status of a background job.
- When a previous Gradle build has failed and you need to diagnose the cause.

## Workflows

### Running a Foreground Command

1. Use `run_gradle_command` with the `commandLine` as an array of strings (e.g. `["clean", "build"]`).
2. If you only need output from a single task (e.g. `:app:dependencies`), use `run_single_task_and_get_output`.
3. If the build fails, the output will indicate the error.
4. For detailed failure analysis, see the [Investigating Build Failures](#investigating-build-failures) section.

### Running a Background Job (e.g. Dev Server)

1. Use `background_run_gradle_command` with the `commandLine`.
2. This tool returns a `BuildId` immediately. Save this ID.
3. Use `background_build_get_status` with the `BuildId` to monitor progress.
    - You can use the `wait` and `waitFor` parameters to wait for a specific log message (e.g., "Started Application").
4. Use `background_build_list` to see all currently active background jobs.
5. Use `background_build_stop` when the job is no longer needed.

### Investigating an Already Run Build

If a build was already executed, you can still investigate it using its `BuildId`.

1. Use `lookup_latest_builds` to find the `BuildId` and basic details (start time, duration, tasks) of recent builds.
2. Use `lookup_build(buildId=ID)` to confirm details of a specific build.
3. Use `lookup_build_failures(buildId=ID, summary={})` to get structured failure reports.
4. Use `lookup_build_problems(buildId=ID, summary={})` for detailed problem diagnostics.
5. Use `lookup_build_console_output(buildId=ID)` to see the full console logs.
6. Use `lookup_build_tasks(buildId=ID)` to see which specific tasks failed or were skipped.

### Investigating Build Failures

1. Identify the `BuildId` (provided in the result or found via `lookup_latest_builds`).
2. Use `lookup_build(buildId=ID)` to get general information about the build (start time, duration, outcome).
3. Use `lookup_build_failures(buildId=ID, summary={})` to get a summary of all failures.
4. Use `lookup_build_problems(buildId=ID, summary={})` to see structured problem reports.
5. If you need more than the last few lines of the log, use `lookup_build_console_output`.
6. Use `lookup_build_tasks` to see which tasks were executed and their outcomes.
7. For complex failures, re-run with `scan: true` and use a Develocity MCP server.

## Examples

### Inspect Dependencies

```json
{
  "taskPath": ":app:dependencies",
  "arguments": [
    "--configuration",
    "implementation"
  ]
}
```

### Start a Dev Server in Background

```json
// Call background_run_gradle_command
{
  "commandLine": [
    ":app:bootRun"
  ],
  "projectRoot": "."
}
// Response: "build_20240301_120000_abc123"

// Wait for it to start
{
  "buildId": "build_20240301_120000_abc123",
  "wait": 30,
  "waitFor": "Started Application in"
}
```

### Investigate a build failure

```json
// Call lookup_build_failures
{
  "buildId": "build_20240301_120500_xyz456",
  "summary": {}
}
```

## Troubleshooting

- **ToS for Build Scans**: If you use `scan: true`, you might be prompted to accept the Terms of Service. Use the `accept_scans_tos` tool if required.
- **Build Not Found**: If a `BuildId` is not recognized, it might have expired. Use `lookup_latest_builds` to find recent IDs.
