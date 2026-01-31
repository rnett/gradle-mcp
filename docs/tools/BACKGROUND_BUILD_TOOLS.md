[//]: # (@formatter:off)

# Background Build Tools

Tools for running and managing Gradle builds in the background.

## background_run_gradle_command

Starts a Gradle command in the background. Returns the BuildId immediately.
Always prefer using this tool over invoking Gradle via the command line or shell.
Use `background_build_get_status` to monitor the build's progress and see the console output.
Once the build is complete, use the `lookup_*` tools to get detailed results, just like a foreground build.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "commandLine": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "The Gradle command to run. Will be ran as if it had been passed directly to './gradlew'"
    },
    "scan": {
      "type": "boolean",
      "description": "Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended over `includeFailureInformation` when possible. Defaults to false."
    },
    "invocationArguments": {
      "type": "object",
      "required": [],
      "properties": {
        "additionalEnvVars": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional environment variables to set for the Gradle process. Optional. The process inherits the MCP server's env vars unless `doNotInheritEnvVars` is set to true. Note that the MCP server may not have the same env vars as the MCP Host - you may need to pass sone."
        },
        "additionalSystemProps": {
          "type": "object",
          "additionalProperties": {
            "type": "string"
          },
          "description": "Additional system properties to set for the Gradle process. Optional. No system properties are inherited from the MCP server."
        },
        "additionalJvmArgs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional JVM arguments to set for the Gradle process. Optional."
        },
        "additionalArguments": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "Additional arguments for the Gradle process. Optional."
        },
        "publishScan": {
          "type": "boolean",
          "description": "Whether to attempt to publish a Develocity Build Scan by using the '--scan' argument. Optional, defaults to false. Using Build Scans is the best way to investigate failures, especially if you have access to the Develocity MCP server. Publishing build scans to scans.gradle.com requires the MCP client to support elicitation."
        },
        "doNotInheritEnvVars": {
          "type": "boolean",
          "description": "Defaults to true. If false, will not inherit env vars from the MCP server."
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "commandLine"
  ],
  "type": "object"
}
```


</details>


## background_build_list

Returns a list of all active and recently completed background builds.
The returned BuildIds can be used with `background_build_get_status`, `background_build_stop`, and the `lookup_*` tools.

<details>

<summary>Input schema</summary>


```json
{
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "active": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "buildId",
          "commandLine",
          "status",
          "startTime"
        ],
        "properties": {
          "buildId": {
            "type": "string"
          },
          "commandLine": {
            "type": "array",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "enum": [
              "RUNNING",
              "SUCCESSFUL",
              "FAILED",
              "CANCELLED"
            ]
          },
          "startTime": {
            "type": "string"
          }
        }
      }
    },
    "completed": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "buildId",
          "commandLine",
          "status",
          "startTime",
          "endTime"
        ],
        "properties": {
          "buildId": {
            "type": "string"
          },
          "commandLine": {
            "type": "array",
            "items": {
              "type": "string"
            }
          },
          "status": {
            "enum": [
              "RUNNING",
              "SUCCESSFUL",
              "FAILED",
              "CANCELLED"
            ]
          },
          "startTime": {
            "type": "string"
          },
          "endTime": {
            "type": "string"
          }
        }
      }
    }
  },
  "required": [
    "active",
    "completed"
  ],
  "type": "object"
}
```


</details>

## background_build_get_status

Returns the detailed status of a background build, including its current status and the recent console output.
For completed builds, it returns a summary of the result.

Arguments:
 - buildId: The build ID of the build to look up.
 - maxTailLines: The maximum number of lines of console output to return. Defaults to 20.

Use the other `lookup_*` tools for more detailed information about completed builds, such as test results or build failures.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "buildId": {
      "type": [
        "string",
        "null"
      ]
    },
    "maxTailLines": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## background_build_stop

Requests that an active background build be stopped. Use `background_build_list` to see active builds.

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
      "description": "The build ID of the build to look up."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




