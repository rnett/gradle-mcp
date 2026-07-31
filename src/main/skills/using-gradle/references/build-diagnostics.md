<!--
class: authored-local
skill: using-gradle
-->
# Build Diagnostics

Diagnoses build failures using `query_build`, `wait_build`, and diagnostic Gradle tasks.

## Surgical Inspection with `query_build`

`query_build` provides structured, token-efficient access to build results. Prefer it over raw console logs.

### Query Kinds

| Kind | Purpose | Query Behavior |
|------|---------|---------------|
| `DASHBOARD` | Recent builds overview | Default; pass `buildId` for detail |
| `CONSOLE` | Raw build logs | Regex filter via `query` |
| `TASKS` | Task execution details | Prefix filter on task path |
| `TESTS` | Test output & stack traces | Prefix filter on test name |
| `FAILURES` | Build failure details | Exact FailureId match |
| `PROBLEMS` | Compilation/config problems | Exact ProblemId match |

### Build Dashboard

```json
{}
```

Call with no arguments to see recent builds. Pass `buildId` for a detailed summary.

### Failure Investigation

1. Get the `BuildId` from the build result.
2. List failures: `query_build(buildId=ID, kind="FAILURES")`.
3. Get details: `query_build(buildId=ID, kind="FAILURES", query="FAILURE_ID")`.

### Problem Investigation

1. List problems: `query_build(buildId=ID, kind="PROBLEMS")`.
2. Get details: `query_build(buildId=ID, kind="PROBLEMS", query="PROBLEM_ID")`.

### Task Output Inspection

```json
{
  "buildId": "BUILD_ID",
  "kind": "TASKS",
  "query": ":app:compileKotlin"
}
```

### Console Log Search

```json
{
  "buildId": "BUILD_ID",
  "kind": "CONSOLE",
  "query": "error|warning|FAILED"
}
```

The `query` acts as a regex filter over console output.

## Diagnostic Gradle Tasks

### Configuration Cache Check

```json
{
  "commandLine": [":help", "--configuration-cache"]
}
```

Review warnings about incompatible plugins or task implementations.

### Build Scan

```json
{
  "commandLine": ["clean", "build", "--scan"]
}
```

Generates a build scan URL for deep performance analysis.

### Dependency Insight

```json
{
  "commandLine": [":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"],
  "captureTaskOutput": ":app:dependencyInsight"
}
```

## Progressive Disclosure

Monitoring a background build via `query_build` or `wait_build` provides the same diagnostic data as foreground execution. The difference is control flow: background allows non-blocking work; foreground blocks until completion.

## Troubleshooting

- **Build fails immediately**: Check `failures` and `console` via `query_build`.
- **No failures reported but build failed**: Check `problems` for compilation/config issues.
- **Stale build state**: Use `query_build()` dashboard to identify and stop orphaned builds.
