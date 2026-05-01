[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## query_build


Queries build information.

If called with no arguments, returns a dashboard of recent builds.

### Query Kinds
- DASHBOARD (default): Recent builds. If buildId is provided, shows a detailed summary of that build.
- CONSOLE: Console logs. `query` acts as a regex filter.
- TASKS: Task outputs. `query` acts as a prefix filter on the task path.
- TESTS: Test outputs. `query` acts as a prefix filter on the test name.
- FAILURES: Build failures. `query` is the exact FailureId.
- PROBLEMS: Compilation/configuration problems. `query` is the exact ProblemId.

If a query for TASKS, TESTS, FAILURES, or PROBLEMS matches exactly one item, it auto-expands to full details. Otherwise, it returns a summary list with a hint to refine the query.
See query_build(kind='CONSOLE', buildId='...') for full logs.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "BuildId to query. If omitted and kind=DASHBOARD, shows recent builds."
    },
    "kind": {
      "enum": [
        "DASHBOARD",
        "CONSOLE",
        "TASKS",
        "TESTS",
        "FAILURES",
        "PROBLEMS"
      ],
      "description": "The aspect of the build to query. Default is DASHBOARD.",
      "type": "string"
    },
    "query": {
      "type": [
        "string",
        "null"
      ],
      "description": "A query string. Acts as a prefix filter for tasks/tests, or a regex for CONSOLE. For failures/problems, it must be the exact ID."
    },
    "outputFile": {
      "type": [
        "string",
        "null"
      ],
      "description": "Output file to save the result. Useful for large console logs."
    },
    "pagination": {
      "type": "object",
      "required": [],
      "properties": {
        "offset": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "limit": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      },
      "description": "Pagination settings. offset = zero-based start index (default 0); limit = max items/lines to return."
    },
    "outcome": {
      "enum": [
        "SUCCESS",
        "FAILED",
        "SKIPPED",
        "UP_TO_DATE",
        "FROM_CACHE",
        "NO_SOURCE",
        "CANCELLED",
        "IN_PROGRESS"
      ],
      "description": "Filter tasks or tests by outcome (e.g. SUCCESS, FAILED).",
      "type": "string"
    },
    "testIndex": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Index of test to show when multiple tests share the same name."
    },
    "taskPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filter tests by task path (prefix). Only applicable when kind=TESTS."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## wait_build


Waits for a background build to reach a specific condition and returns the final console tail.

Use `timeout` (seconds) with `waitFor` (regex), `waitForTask` (path), or `waitForFinished=true` to monitor active builds.
If no wait condition (regex or task) is provided, it defaults to waiting for the build to finish.

Set `afterCall=true` to only match events emitted after this call.
See query_build(kind='CONSOLE', buildId='...') for full logs.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": "string",
      "description": "BuildId to wait for."
    },
    "timeout": {
      "type": "number",
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Max seconds to wait. Default is 600.0."
    },
    "waitForFinished": {
      "type": "boolean",
      "description": "Wait for the build to finish. Default if no other wait condition is provided."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex to wait for in build logs (e.g., server started)."
    },
    "waitForTask": {
      "type": [
        "string",
        "null"
      ],
      "description": "Task path to wait for completion."
    },
    "afterCall": {
      "type": "boolean",
      "description": "Only match events emitted after this call."
    }
  },
  "required": [
    "buildId"
  ],
  "type": "object"
}
```


</details>




