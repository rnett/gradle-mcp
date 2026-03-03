[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## inspect_build

The central tool for retrieving build information, monitoring progress, and diagnosing failures. It acts as a "Dashboard" and "Deep Dive" tool for all builds (active and historical).

### Usage Overview
- **Dashboard**: Call without `buildId` to see active and recent builds.
- **Monitoring**: Use `wait`, `waitFor`, or `waitForTask` with a `buildId` to monitor an active build.
- **Deep Dive**: Specify a `buildId` and exactly one of the detail sections (`tasks`, `tests`, `failures`, `problems`, or `console`).
- **Modes**: Use `mode="summary"` (default) for lists and `mode="details"` for deep dives into specific items (requires `name`, `path`, or `id` in the section options).
- **Pagination**: Use top-level `limit` and `offset` for lists and console output.

### Section Options
Only one of the following may be specified per call:
- `tasks`: List tasks or get task output.
- `tests`: List tests or get test details/stack traces.
- `failures`: List build failures or get failure trees.
- `problems`: List build problems or get problem details.
- `console`: Read console logs with pagination.

If no section is specified, the build summary is returned.

For detailed diagnostic workflows, refer to the `gradle-build` and `gradle-test` skills.

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
      "description": "The build to inspect. If omitted, returns the build dashboard (active builds + recent history)."
    },
    "mode": {
      "enum": [
        "summary",
        "details"
      ],
      "description": "The lookup mode: 'summary' (default) or 'details'."
    },
    "wait": {
      "type": [
        "number",
        "null"
      ],
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Max seconds to wait for an active build to reach a state or finish."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex pattern to wait for in the build logs."
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
      "description": "If true, only look for waitFor or waitForTask matches emitted after this call. Only applies if wait and (waitFor or waitForTask) are also provided."
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of results to return (applies to tasks, tests, console lines, and the build dashboard)."
    },
    "offset": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The offset to start from in the results (applies to tasks, tests, and console lines)."
    },
    "tasks": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "path": {
          "type": "string",
          "description": "In 'summary' mode, a prefix of the task path to filter by. In 'details' mode, the full path of the task."
        },
        "outcome": {
          "enum": [
            "SUCCESS",
            "FAILED",
            "SKIPPED",
            "UP_TO_DATE",
            "FROM_CACHE",
            "NO_SOURCE"
          ],
          "description": "Filter results by outcome (summary mode only)."
        }
      },
      "description": "Options for task lookup."
    },
    "tests": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "name": {
          "type": "string",
          "description": "In 'summary' mode, a prefix of the test name to filter by. In 'details' mode, the full name of the test."
        },
        "outcome": {
          "enum": [
            "PASSED",
            "FAILED",
            "SKIPPED"
          ],
          "description": "Filter results by outcome (summary mode only)."
        },
        "testIndex": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647,
          "description": "The index of the test to show if multiple tests have the same name (details mode only)."
        }
      },
      "description": "Options for test lookup."
    },
    "failures": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "id": {
          "type": [
            "string",
            "null"
          ],
          "description": "The failure ID to get details for (details mode only)."
        }
      },
      "description": "Options for failure lookup."
    },
    "problems": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "id": {
          "type": [
            "string",
            "null"
          ],
          "description": "The ProblemId of the problem to look up (details mode only)."
        }
      },
      "description": "Options for problem lookup."
    },
    "console": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "tail": {
          "type": "boolean",
          "description": "If true, return the last `limit` lines of the console output instead of the first."
        }
      },
      "description": "Options for console output."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




