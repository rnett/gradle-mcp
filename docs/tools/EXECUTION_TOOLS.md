[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## gradlew

The primary tool for managing the Gradle build lifecycle. It can start builds (foreground/background) and stop active background builds.

Use this tool for:
- Running any Gradle task (e.g., `build`, `test`, `clean`).
- Starting long-running background processes like development servers.
- Stopping active background builds using their `BuildId`.
- Getting clean output from a specific task using `captureTaskOutput`.

After running a build, use the `inspect_build` tool with the returned `BuildId` to monitor progress or investigate failures.
For detailed workflows on background monitoring and failure analysis, refer to the `gradle-build` skill.

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
      "type": [
        "array",
        "null"
      ],
      "items": {
        "type": "string"
      },
      "description": "The arguments for gradlew (e.g., [\":app:test\", \"--tests\", \"MyTest\"]). Required if not stopping a build."
    },
    "background": {
      "type": "boolean",
      "description": "If true, starts in background and returns BuildId. Defaults to false."
    },
    "stopBuildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The BuildId of an active build to stop. If present, all other args are ignored."
    },
    "captureTaskOutput": {
      "type": [
        "string",
        "null"
      ],
      "description": "Path of a task to extract and return output for exclusively."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




