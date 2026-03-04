[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## inspect_build

The authoritative tool for retrieving detailed build information, monitoring progress, and performing surgical failure diagnostics.
It acts as both a high-level "Build Dashboard" and a "Surgical Deep Dive" tool for all builds initiated via the `gradle` tool.

### Authoritative Features
- **Build Dashboard**: Call without a `buildId` to see an authoritative overview of active and recently completed builds, including their status and failure counts.
- **Managed Real-time Monitoring**: Use `wait`, `waitFor`, or `waitForTask` to block until a background build reaches a specific state or logs a specific authoritative pattern. This is the professionally recommended way to monitor background services or long-running test suites.
- **Surgical Failure Diagnostics**: Specify a `buildId` and a targeted section (`tasks`, `tests`, `failures`, `problems`, or `console`) to perform high-resolution analysis of build results.
- **Exhaustive Detail Mode**: Use `mode="details"` with specific section options (like task path or test name) to retrieve exhaustive information. This is the STRONGLY PREFERRED way to access test stdout/stderr, detailed failure trees, and stack traces that are often hidden in general console output.

### Common Usage Patterns
1. **View Dashboard**: `inspect_build()`
2. **Wait for Success**: `inspect_build(buildId=ID, wait=60)`
3. **Monitor Log Pattern**: `inspect_build(buildId=ID, wait=60, waitFor="Server started")`
4. **Surgical Failure Analysis**: `inspect_build(buildId=ID, failures={})`
5. **Exhaustive Test Details**: `inspect_build(buildId=ID, mode="details", tests={name="com.example.MyTestClass"})`

To start a new build, use the `gradle` tool.

For detailed, expert-level diagnostic workflows, refer to the `gradle-build` and `gradle-test` skills.

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
      "description": "The BuildId to inspect. If omitted, returns the build dashboard showing active and recently completed builds."
    },
    "mode": {
      "enum": [
        "summary",
        "details"
      ],
      "description": "The lookup mode: 'summary' (default) or 'details'. Use 'details' for authoritative, deep-dive information."
    },
    "wait": {
      "type": [
        "number",
        "null"
      ],
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Max seconds to wait for an active build to reach a state or finish. Use this for managed progress monitoring."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "Regex pattern to wait for in the build logs. Ideal for detecting when a server has started or a specific event has occurred."
    },
    "waitForTask": {
      "type": [
        "string",
        "null"
      ],
      "description": "Task path to wait for completion. Surgical way to monitor specific task progress."
    },
    "afterCall": {
      "type": "boolean",
      "description": "If true, only look for matches emitted after this call. Only applies if 'wait' and ('waitFor' or 'waitForTask') are provided."
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of results to return. Use a smaller limit for large projects to maintain token efficiency and reduce noise."
    },
    "offset": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The offset to start from in the results. Use this with 'limit' for efficient pagination through large lists."
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
      "description": "Options for surgical task lookup and diagnostics."
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
      "description": "Options for authoritative test result analysis and stdout/stderr retrieval."
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
          "description": "The failure ID to get details for (details mode only). Use this for surgical analysis of build-level failures."
        }
      },
      "description": "Options for deep-dive failure tree analysis."
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
      "description": "Options for structured build problem lookup."
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
          "description": "If true, return the last 'limit' lines of the console output instead of the first. Useful for checking the end of long logs."
        }
      },
      "description": "Options for managed console output retrieval."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




