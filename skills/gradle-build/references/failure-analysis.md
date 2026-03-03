# Investigating Build Failures

This guide provides advanced workflows for diagnosing complex Gradle build failures using `inspect_gradle_build`.

## Diagnostic Workflow

When a build fails, follow these steps to identify the root cause:

### 1. Get the Failure Summary

Start by getting a high-level overview of what went wrong.

```json
{
  "buildId": "BUILD_ID",
  "failures": {}
}
```

- **`failures`**: Provides a structured report of the main build failure(s).
- **`problems`**: Shows detailed problem reports, including file locations and suggestions.

### 2. Inspect Failed Tasks

If the failure is task-related, check which tasks failed and their outcomes.

```json
{
  "buildId": "BUILD_ID",
  "mode": "details",
  "tasks": {
    "path": ":app:compileJava"
  }
}
```

- Use `tasks.path` in `mode: "details"` to get specific information about a failed task.

### 3. Deep Dive into Console Logs

If the structured reports aren't enough, examine the console output.

```json
{
  "buildId": "BUILD_ID",
  "console": {
    "tail": true
  },
  "limit": 100
}
```

- Use `tail` to see the last few lines of the log.
- Use `offset` and `limit` to paginate through large logs.

### 4. Check for Test Failures

If the build failed during testing, use the test-specific inspection.

```json
{
  "buildId": "BUILD_ID",
  "tests": {}
}
```

- This will list failed tests. For more details on test diagnostics, see `gradle-test/references/test-diagnostics.md`.

## Common Failure Scenarios

### Compilation Errors

- Look for `problems` with `severity: ERROR`.
- Check the `console` output for the specific compiler error message and file location.

### Dependency Resolution Issues

- Check `failures` for messages like "Could not resolve all dependencies".
- Use `inspect_gradle_dependencies` to investigate the dependency graph.

### Task Execution Failures

- Check `tasks` to see which task failed.
- Inspect the `console` output for that specific task.

### Build Script Errors

- These usually appear in the `failures` section with a stack trace or a pointer to the line in `build.gradle.kts`.

## Advanced Diagnostics

### Build Scans

If the failure is particularly elusive, re-run the build with `--scan` and use a Develocity MCP server for even deeper analysis.

```json
{
  "commandLine": ["build", "--scan"]
}
```

*(Note: You may need to use `accept_scans_tos` if prompted.)*
