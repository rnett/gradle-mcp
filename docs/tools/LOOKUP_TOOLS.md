[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## inspect_build

ALWAYS use this tool to inspect detailed build information, monitor progress, and perform surgical failure diagnostics instead of reading raw console logs.
To inspect test failures or outputs, ALWAYS use `testName` with `mode="details"`. DO NOT use `taskPath` or `captureTaskOutput` for tests, as they lack per-test isolation and truncate output.
This is the most token-efficient and reliable way to get specific test stdout/stderr, full task outputs, and deep-dive failure trees which are often obscured or interleaved in raw console output.
Provides a managed interface to wait for specific log patterns, check active builds (omit `buildId`), or get detailed outputs (use `mode="details"` with `testName`, `taskPath`, etc).
For deep guidance on diagnostics, refer to the `managing_gradle_builds` and `executing_gradle_tests` skills if installed.

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
      "description": "The managed BuildId to inspect authoritatively. If omitted, returns the high-level build dashboard showing active and recently completed builds."
    },
    "mode": {
      "enum": [
        "summary",
        "details"
      ],
      "description": "Applying a surgical lookup mode: 'summary' (default) or 'details'. Use 'details' for exhaustive, deep-dive information."
    },
    "wait": {
      "type": [
        "number",
        "null"
      ],
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Maximum seconds to wait for an active build to reach a state or finish authoritatively. Use this for managed progress monitoring."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex pattern to wait for in the build logs authoritatively. Ideal for detecting when a server has started or a specific event has occurred."
    },
    "waitForTask": {
      "type": [
        "string",
        "null"
      ],
      "description": "Task path to wait for completion authoritatively. The most surgical way to monitor specific task progress."
    },
    "afterCall": {
      "type": "boolean",
      "description": "Setting to true only looks for matches emitted after this call. Only applies if 'wait' and ('waitFor' or 'waitForTask') are provided."
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
      "description": "Filter task results. In 'summary' mode, a prefix of the task path. In 'details' mode, the full path of the task. Specify this to get task details. DO NOT use this for tests; use testName instead."
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
      "description": "Filter task results by outcome (summary mode only)."
    },
    "testName": {
      "type": [
        "string",
        "null"
      ],
      "description": "Filter test results. In 'summary' mode, a prefix of the test name. In 'details' mode, the full name of the test. Specify this to get test details. ALWAYS use this with `mode=\"details\"` instead of taskPath to see individual test outputs, metadata, and stack traces. Generic task output lacks test-specific diagnostic information."
    },
    "testOutcome": {
      "enum": [
        "PASSED",
        "FAILED",
        "SKIPPED"
      ],
      "description": "Filter test results by outcome (summary mode only)."
    },
    "testIndex": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The index of the test to show if multiple tests have the same name (details mode only)."
    },
    "failureId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The failure ID to get details for (details mode only). Use this for surgical analysis of build-level failures."
    },
    "problemId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The ProblemId of the problem to look up (details mode only)."
    },
    "consoleTail": {
      "type": [
        "boolean",
        "null"
      ],
      "description": "If true, return the last 'limit' lines of the console output instead of the first. Useful for checking the end of long logs. Specify this to get raw console output."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




