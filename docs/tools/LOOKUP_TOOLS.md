[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## inspect_build

Surgically inspects detailed build information, monitors progress, and performs post-mortem diagnostics.
ALWAYS use this tool to investigate test failures, task outputs, and build-level errors instead of reading raw console logs.

### Surgical Lookup Modes

1.  **Summary Mode (`mode="summary"`)**
    -   **Best for**: Finding BuildIds, TaskPaths, TestNames, and FailureIds.
    -   **Default behaviour**: Shows a high-level dashboard of recent builds if `buildId` is omitted.

2.  **Details Mode (`mode="details"`)**
    -   **Best for**: Exhaustive analysis of a specific item (requires `testName`, `taskPath`, `failureId`, or `problemId`).
    -   **Crucial for Tests**: ALWAYS use `mode="details"` with `testName` to see the individual test case's console output, metadata, and stack trace. THIS IS THE ONLY WAY TO SEE THE TEST'S CONSOLE OUTPUT.

### How to Inspect Details

- **Individual Tests (INCLUDING TEST CONSOLE OUTPUT)**:  `testName="FullTestName"`, `mode="details"` (REQUIRED for full output/stack trace).
- **Task Outputs**:      `taskPath=":path:to:task"`, `mode="details"`.
- **Build Failures**:    `failureId="ID"`, `mode="details"` (use summary mode first to find IDs).
- **Problems/Errors**:   `problemId="ID"`, `mode="details"` (use summary mode first to find IDs).
- **Full Console (EXCEPT TESTS)**:      `consoleTail=true` (tail) or `consoleTail=false` (head).

### Wait & Progress Monitoring
- Use `timeout` (seconds) with `waitFor` (regex), `waitForTask` (path), or `waitForFinished` (boolean) to monitor active builds and real-time test progress (e.g., pass/fail counts).
- If `timeout` is omitted, the tool returns immediately with the current build status.
- If `timeout` is provided but `waitFor` and `waitForTask` are omitted, the tool defaults to waiting for the build to finish (equivalent to `waitForFinished=true`).
- If the build finishes before the requested regex or task is found, the tool returns an error.
- Set `afterCall=true` to only look for events emitted after the tool is called.

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
      "description": "Providing the managed BuildId to inspect authoritatively. If omitted, returns the high-level build dashboard showing active and recently completed builds."
    },
    "mode": {
      "enum": [
        "summary",
        "details"
      ],
      "description": "Applying a surgical lookup mode: 'summary' (default) or 'details'. Use 'details' for exhaustive, deep-dive information.",
      "type": "string"
    },
    "timeout": {
      "type": [
        "number",
        "null"
      ],
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Specifying the maximum number of seconds to wait for the requested condition(s). If omitted, the tool returns immediately with the current build status."
    },
    "waitForFinished": {
      "type": "boolean",
      "description": "Waiting for the build to finish authoritatively. This is the default behavior if 'timeout' is provided but no other wait conditions are specified."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "Providing a regex pattern to wait for in the build logs authoritatively. Ideal for detecting when a server has started or a specific event has occurred. Uses 'timeout' for the maximum wait duration."
    },
    "waitForTask": {
      "type": [
        "string",
        "null"
      ],
      "description": "Providing a task path to wait for completion authoritatively. The most surgical way to monitor specific task progress. Uses 'timeout' for the maximum wait duration."
    },
    "afterCall": {
      "type": "boolean",
      "description": "Setting to true only looks for matches emitted after this call. Only applies if 'timeout' and a wait condition ('waitFor', 'waitForTask', or 'waitForFinished') are provided."
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
      "description": "Pagination parameters. Offset is the zero-based starting index (defaults to 0). Limit is the maximum number of items/lines to return."
    },
    "taskPath": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filtering task results. In 'summary' mode, a prefix of the task path. In 'details' mode, the full path of the task. Specify this to get task details. DO NOT use this for tests; use testName instead."
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
      "description": "Filtering task results by outcome (summary mode only).",
      "type": "string"
    },
    "testName": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filtering test results. In 'summary' mode, a prefix of the test name. In 'details' mode, the full name of the test. Specify this to get test details. ALWAYS use this with `mode=\"details\"` instead of taskPath to see individual test outputs, metadata, and stack traces. Generic task output lacks test-specific diagnostic information."
    },
    "testOutcome": {
      "enum": [
        "PASSED",
        "FAILED",
        "SKIPPED",
        "CANCELLED",
        "IN_PROGRESS"
      ],
      "description": "Filtering test results by outcome (summary mode only).",
      "type": "string"
    },
    "testIndex": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Specifying the index of the test to show if multiple tests have the same name (details mode only)."
    },
    "failureId": {
      "type": [
        "string",
        "null"
      ],
      "description": "Providing the failure ID to get details for (details mode only). Use this for surgical analysis of build-level failures."
    },
    "problemId": {
      "type": [
        "string",
        "null"
      ],
      "description": "Providing the ProblemId of the problem to look up (details mode only)."
    },
    "consoleTail": {
      "type": [
        "boolean",
        "null"
      ],
      "description": "Returning the last 'limit' lines of the console output instead of the first. Useful for checking the end of long logs. Specify this to get raw console output."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




