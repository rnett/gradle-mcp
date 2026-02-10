[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## lookup_latest_builds

Gets the latest builds (both background and completed) ran by this MCP server.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "maxBuilds": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of builds to return. Defaults to 5."
    },
    "onlyCompleted": {
      "type": "boolean",
      "description": "Whether to only show completed builds. Defaults to false."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_tests

For a given build, provides either a summary of test executions or detailed information for a specific test. If `details` is provided, detailed execution info (duration, failure details, and console output) for that test is returned. If `summary` is provided (or neither), returns a list of tests matching the provided filters. Only one of `summary` or `details` may be specified.

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
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "summary": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "testNamePrefix": {
          "type": "string",
          "description": "A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name. Defaults to empty (aka all tests)."
        },
        "offset": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647,
          "description": "The offset to start from in the results."
        },
        "limit": {
          "type": [
            "integer",
            "null"
          ],
          "minimum": -2147483648,
          "maximum": 2147483647,
          "description": "The maximum number of results to return."
        },
        "outcome": {
          "enum": [
            "PASSED",
            "FAILED",
            "SKIPPED"
          ],
          "description": "Filter results by outcome."
        }
      },
      "description": "Arguments for test summary mode. Only one of `summary` or `details` may be specified."
    },
    "details": {
      "type": [
        "object",
        "null"
      ],
      "required": [
        "testName"
      ],
      "properties": {
        "testName": {
          "type": "string",
          "description": "The full name of the test to show details for. If multiple tests have this name, use `testIndex` to select one."
        },
        "testIndex": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647,
          "description": "The index of the test to show if multiple tests have the same name."
        }
      },
      "description": "Arguments for test detail mode. Only one of `summary` or `details` may be specified."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_tasks

For a given build, provides either a summary of task executions or detailed information for a specific task. If `details` is provided, detailed execution info (duration, outcome, and console output) for that task is returned. If `summary` is provided (or neither), returns a list of tasks matching the provided filters. Only one of `summary` or `details` may be specified.

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
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "summary": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {
        "taskPathPrefix": {
          "type": "string",
          "description": "A prefix of the task path (e.g. ':app:'). Matching is case-sensitive and checks startsWith on the task path. Defaults to empty (aka all tasks)."
        },
        "offset": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647,
          "description": "The offset to start from in the results."
        },
        "limit": {
          "type": [
            "integer",
            "null"
          ],
          "minimum": -2147483648,
          "maximum": 2147483647,
          "description": "The maximum number of results to return."
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
          "description": "Filter results by outcome."
        }
      },
      "description": "Arguments for task summary mode. Only one of `summary` or `details` may be specified."
    },
    "details": {
      "type": [
        "object",
        "null"
      ],
      "required": [
        "taskPath"
      ],
      "properties": {
        "taskPath": {
          "type": "string",
          "description": "The full path of the task to show details for."
        }
      },
      "description": "Arguments for task detail mode. Only one of `summary` or `details` may be specified."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build

Takes a build ID; returns a summary of that build.

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
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_failures

Provides a summary of build failures (not including test failures) or details for a specific failure. If `details` is provided, detailed information (including causes and stack traces) for that failure is returned. If `summary` is provided (or neither), lists all build failures. Only one of `summary` or `details` may be specified.

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
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "summary": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {},
      "description": "Arguments for failure summary mode. Only one of `summary` or `details` may be specified."
    },
    "details": {
      "type": [
        "object",
        "null"
      ],
      "required": [
        "failureId"
      ],
      "properties": {
        "failureId": {
          "type": "string",
          "description": "The failure ID to get details for."
        }
      },
      "description": "Arguments for failure detail mode. Only one of `summary` or `details` may be specified."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_problems

Provides a summary of all problems reported during a build (errors, warnings, etc.) or details for a specific problem. If `details` is provided, detailed information (locations, details, and potential solutions) for that problem is returned. If `summary` is provided (or neither), returns a summary of all problems. Only one of `summary` or `details` may be specified.

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
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "summary": {
      "type": [
        "object",
        "null"
      ],
      "required": [],
      "properties": {},
      "description": "Arguments for problem summary mode. Only one of `summary` or `details` may be specified."
    },
    "details": {
      "type": [
        "object",
        "null"
      ],
      "required": [
        "problemId"
      ],
      "properties": {
        "problemId": {
          "type": "string",
          "description": "The ProblemId of the problem to look up."
        }
      },
      "description": "Arguments for problem detail mode. Only one of `summary` or `details` may be specified."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_console_output

Gets up to `limitLines` (default 100, null means no limit) of the console output for a given build, starting at a given offset `offsetLines` (default 0). Can read from the tail instead of the head. Repeatedly call this tool using the `nextOffset` in the response to get all console output.

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
      "description": "The build ID of the build to look up. Defaults to the most recent build ran by this MCP server."
    },
    "offsetLines": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "limitLines": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "tail": {
      "type": "boolean"
    }
  },
  "required": [
    "offsetLines"
  ],
  "type": "object"
}
```


</details>




