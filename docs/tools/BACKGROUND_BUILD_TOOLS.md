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
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If the GRADLE_MCP_PROJECT_ROOT environment variable is set, it will be used as the default if no root is specified and no MCP root is registered. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
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
          "description": "Additional environment variables to set for the Gradle process. Optional."
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
        "envSource": {
          "enum": [
            "NONE",
            "INHERIT",
            "SHELL"
          ],
          "description": "Where to get the environment variables from to pass to Gradle. Defaults to INHERIT. SHELL starts a new shell process and queries its env vars."
        },
        "requestedInitScripts": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "description": "The names of the init scripts to load. Defaults to empty list."
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

Returns a list of all active background builds.
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


## background_build_get_status

Returns the detailed status of a background build, including its current status and the recent console output.
For completed builds, it returns a summary of the result.

Arguments:
 - buildId: The build ID of the build to look up.
 - maxTailLines: The maximum number of lines of console output to return. Defaults to 20.
 - wait: Wait for the build to complete or for the waitFor regex to be seen in the output, or for waitForTask to complete, for up to this many seconds.
 - waitFor: A regex to wait for in the output. If seen, the wait will short-circuit, and all matching lines will be returned.
 - waitForTask: A task path to wait for. If seen, the wait will short-circuit.
 - afterCall: If true, only look for waitFor or waitForTask matches emitted after this call. Only applies if wait and (waitFor or waitForTask) are also provided.

 Use the other `lookup_*` tools for more detailed information about both running and completed builds, such as test results or build failures.

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
    },
    "wait": {
      "type": [
        "number",
        "null"
      ],
      "minimum": -1.7976931348623157E308,
      "maximum": 1.7976931348623157E308,
      "description": "Wait for the build to complete or for the waitFor regex to be seen in the output, for up to this many seconds."
    },
    "waitFor": {
      "type": [
        "string",
        "null"
      ],
      "description": "A regex to wait for in the output. If seen, the wait will short-circuit, and all matching lines will be returned."
    },
    "waitForTask": {
      "type": [
        "string",
        "null"
      ],
      "description": "A task path to wait for. If seen, the wait will short-circuit."
    },
    "afterCall": {
      "type": "boolean",
      "description": "If true, only look for waitFor or waitForTask matches emitted after this call. Only applies if wait and (waitFor or waitForTask) are also provided."
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




