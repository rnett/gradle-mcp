[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## inspect_build

Inspects build information, monitors progress, and performs post-mortem diagnostics; ALWAYS use instead of raw console logs for test failures, task outputs, and build errors.

**Note:** Only builds executed by this MCP server session are listed. External Gradle runs are not tracked.

### Lookup Modes
- **`mode="summary"`** (default): Dashboard/overview; best for finding BuildIds, TestNames, FailureIds. When a build ID is provided, shows a detailed summary including recent error context and currently running tasks.
- **`mode="details"`**: Exhaustive analysis; requires `testName`, `taskPath`, `failureId`, or `problemId`.

### How to Inspect Details
- Tests (incl. console output): `testName="FullTestName"`, `mode="details"` — REQUIRED for stack traces and test output.
- Task outputs: `taskPath=":path:to:task"`, `mode="details"`.
- Build failures: `failureId="ID"`, `mode="details"` (use summary first to find IDs).
- Full console: `consoleTail=true` (tail) or `consoleTail=false` (head).
- Pagination: Use `offset` and `limit` to navigate through long console logs or large task/test lists.

### Wait & Progress Monitoring
Use `timeout` (seconds) with `waitFor` (regex), `waitForTask` (path), or `waitForFinished=true` to monitor active builds.
If `timeout` is set but no wait condition is specified, defaults to waiting for the build to finish.
Set `afterCall=true` to only match events emitted after this call.

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
      "description": "BuildId to inspect. If omitted, shows the active/recent builds dashboard."
    },
    "mode": {
      "enum": [
        "summary",
        "details"
      ],
      "description": "'summary' (default) or 'details'. Use 'details' with testName/taskPath for full output.",
      "type": "string"
    },
    "timeout": {
      "type": [
        "number",
        "null"
      ],
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Max seconds to wait for a condition. If omitted, returns immediately with current status."
    },
    "waitForFinished": {
      "type": "boolean",
      "description": "Wait for the build to finish. Default if 'timeout' is set and no other wait condition is provided."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex to wait for in build logs (e.g., server started). Requires 'timeout'."
    },
    "waitForTask": {
      "type": [
        "string",
        "null"
      ],
      "description": "Task path to wait for completion. Requires 'timeout'."
    },
    "afterCall": {
      "type": "boolean",
      "description": "Only match events emitted after this call. Requires 'timeout' and a wait condition."
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
      "description": "Pagination. offset = zero-based start index (default 0); limit = max items/lines to return."
    },
    "taskPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Task path prefix (summary) or full/unique-prefix path (details) for task output and outcome."
    },
    "taskOutcome": {
      "enum": [
        "SUCCESS",
        "FAILED",
        "SKIPPED",
        "UP_TO_DATE",
        "FROM_CACHE",
        "NO_SOURCE"
      ],
      "description": "Filter task results by outcome (summary mode only).",
      "type": "string"
    },
    "testName": {
      "type": [
        "string",
        "null"
      ],
      "description": "Test name prefix (summary) or full/unique prefix (details). Use mode='details' for stack traces."
    },
    "testOutcome": {
      "enum": [
        "PASSED",
        "FAILED",
        "SKIPPED",
        "CANCELLED",
        "IN_PROGRESS"
      ],
      "description": "Filter test results by outcome (summary mode only).",
      "type": "string"
    },
    "testIndex": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Index of test to show when multiple tests share the same name (details mode only)."
    },
    "failureId": {
      "type": [
        "string",
        "null"
      ],
      "description": "Failure ID to get details for (details mode only)."
    },
    "problemId": {
      "type": [
        "string",
        "null"
      ],
      "description": "ProblemId to look up (details mode only)."
    },
    "consoleTail": {
      "type": [
        "boolean",
        "null"
      ],
      "description": "true = tail raw console output; false = head. Specify to get raw console output."
    },
    "outputFile": {
      "type": [
        "string",
        "null"
      ],
      "description": "If specified, write the output to the given file path instead of returning it. The response will include the file absolute path and its length (in characters and lines)."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




