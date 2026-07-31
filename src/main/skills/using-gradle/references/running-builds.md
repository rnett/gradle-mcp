<!--
class: authored-local
skill: using-gradle
-->
# Running Builds

Executes Gradle builds in foreground or background, manages build lifecycle, and captures task output.

## Foreground Execution

The default and preferred mode. Provides progressive disclosure of build output.

```json
{
  "commandLine": ["clean", "build"]
}
```

- **Always use foreground** when you intend to wait for a result.
- **Foreground is safe** for high-output suites — the `gradle` tool uses progressive disclosure.
- **Use `--rerun`** (not `--rerun-tasks`) for individual task re-execution.

## Background Execution

Use `background: true` ONLY for persistent tasks (servers, continuous builds) or when performing parallel work.

```json
{
  "commandLine": ["bootRun"],
  "background": true
}
```

Returns a `BuildId` immediately. Use `wait_build` and `query_build` to monitor.

### Waiting for a Log Message

```json
{
  "buildId": "BUILD_ID",
  "timeout": 60,
  "waitFor": "Started Application"
}
```

### Waiting for Task Completion

```json
{
  "buildId": "BUILD_ID",
  "timeout": 120,
  "waitForTask": ":app:assemble"
}
```

### Monitoring Progress

```json
{
  "buildId": "BUILD_ID"
}
```

Call `query_build(buildId=...)` to get current state without blocking.

### Build Dashboard

Call `query_build()` with no arguments to see all active and recent builds.

### Stopping a Background Build

```json
{
  "stopBuildId": "BUILD_ID"
}
```

**Always** stop background builds when finished to free resources.

## Continuous Builds

```json
{
  "commandLine": ["build", "--continuous"],
  "background": true
}
```

Then wait for `"Waiting for changes"`:

```json
{
  "buildId": "BUILD_ID",
  "timeout": 120,
  "waitFor": "Waiting for changes"
}
```

## `captureTaskOutput` Usage

Use for clean, isolated output from introspection tasks:

| Capture Target | Use Case |
|---------------|----------|
| `":projects"` | Clean project list |
| `":app:tasks"` | Task list for a specific project |
| `":help"` | Task documentation |
| `":properties"` | Property extraction |
| `":app:dependencyInsight"` | Dependency resolution path |

## Handling Timeouts

If `wait_build` times out, it returns the current status. Call again with a new `timeout` if needed.

## Troubleshooting

- **Build Fails Immediately**: Check `failures` and `console` output via `query_build`.
- **Log Message Not Found**: Verify the `waitFor` regex matches actual console output.
- **Resource Exhaustion**: Stop unused background builds via `stopBuildId`.
