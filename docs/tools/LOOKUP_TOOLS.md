[//]: # (@formatter:off)

# Lookup Tools

Tools for looking up detailed information about past Gradle builds ran by this MCP server.

## lookup_latest_builds

Gets the latest builds ran by this MCP server.

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
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_tests

For a given build, gets an overview of test executions matching the prefix.  Control results using `offset` (defaults to 0), `limit` (defaults to 20, pass null to return all), and `outcome` (defaults to null, which includes all)  Use `lookup_build_test_details` to get more details for a specific execution.

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
    "testNamePrefix": {
      "type": "string",
      "description": "A prefix of the fully-qualified test name (class or method). Matching is case-sensitive and checks startsWith on the full test name. Defaults to empty (aka all tests)."
    },
    "offset": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    },
    "outcome": {
      "enum": [
        "PASSED",
        "FAILED",
        "SKIPPED"
      ],
      "description": "The outcome of a test."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## lookup_build_test_details

Gets the details of test execution of the given test.

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
    "testName": {
      "type": "string",
      "description": "The test to show the details of."
    },
    "testIndex": {
      "type": "integer",
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The index of the test to show, if there are multiple tests with the same name"
    }
  },
  "required": [
    "testName"
  ],
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


## lookup_build_failures_summary

For a given build, gets the summary of all build (not test) failures in the build. Use `lookup_build_failure_details` to get the details of a specific failure.

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


## lookup_build_failure_details

For a given build, gets the details of a failure with the given ID. Use `lookup_build_failures_summary` to get a list of failure IDs.

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
    "failureId": {
      "type": "string",
      "description": "The failure ID to get details for."
    }
  },
  "required": [
    "failureId"
  ],
  "type": "object"
}
```


</details>


## lookup_build_problems_summary

For a given build, get summaries for all problems attached to failures in the build. Use `lookup_build_problem_details` with the returned failure ID to get full details.

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


## lookup_build_problem_details

For a given build, gets the details of all occurrences of the problem with the given ID. Use `lookup_build_problems_summary` to get a list of all problem IDs for the build.

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
    "problemId": {
      "type": "string",
      "description": "The ProblemId of the problem to look up. Obtain from `lookup_build_problems_summary`."
    }
  },
  "required": [
    "problemId"
  ],
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




