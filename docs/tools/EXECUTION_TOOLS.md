[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## gradle

The authoritative tool for managing the Gradle build lifecycle.
It is the STRONGLY PREFERRED way to execute any Gradle task, providing a managed environment with features that raw shell execution cannot match.
Unless the Tooling API is demonstrably insufficient for a specific edge case, ALWAYS prefer this tool over direct shell commands.

### High-Performance Features
- **Managed Background Lifecycle**: Execute long-running builds, tests, or servers in the background. Receive a `BuildId` instantly, allowing you to monitor progress or perform other tasks while the build proceeds.
- **Precision Task Output Capturing**: Use `captureTaskOutput` to extract clean, isolated output for a single task. This is the most token-efficient way to read task results like `dependencies`, `help`, or `properties` as it eliminates all background console noise.
- **Surgical Build Control**: Seamlessly stop background processes using `stopBuildId` and transition to the `inspect_build` tool for deep post-mortem failure analysis.
- **Maximum Token Efficiency**: Provides concise summaries for foreground builds and rich, searchable metadata for background ones.

### Common Usage Patterns
- **Standard Build**: `gradle(commandLine=["clean", "build"])`
- **Background Server**: `gradle(commandLine=[":app:bootRun"], background=true)`
- **Clean Dependency Report**: `gradle(commandLine=[":app:dependencies"], captureTaskOutput=":app:dependencies")`

### Task Path Syntax Reference
- `:task` (leading colon): Targets the task in the **root project only**.
- `task` (no leading colon): Targets the task in **all projects** (root and all subprojects).
- `:app:task`: Targets the task in the `app` subproject.

**Safety Note**: Avoid using `--rerun-tasks` unless absolutely necessary, as it bypasses all Gradle caching and significantly increases build time.

### Post-Build Diagnostics
After a build finishes (or while a background build is running), use the `inspect_build` tool with the `BuildId` to:
- Retrieve detailed test failure trees and stack traces.
- Access specific test stdout/stderr (often missing from the general console).
- Tail build logs or wait for specific log patterns.

For expert-level workflows, refer to the `gradle-build` and `gradle-test` skills.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The absolute path to the project root directory. Defaults to the current workspace root. Always provide this if you are working in a multi-root workspace to ensure the correct project is targeted."
    },
    "commandLine": {
      "type": [
        "array",
        "null"
      ],
      "items": {
        "type": "string"
      },
      "description": "The arguments for gradle (e.g., [\":app:test\", \"--tests\", \"MyTest\"]). This is the primary way to specify tasks and flags. Required if not stopping a build."
    },
    "background": {
      "type": "boolean",
      "description": "If true, starts the build in the background and returns a BuildId immediately. STRONGLY RECOMMENDED for long-running tasks like 'build', 'test', or 'bootRun' to maintain agent responsiveness."
    },
    "stopBuildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "The BuildId of an active background build to stop. If provided, all other arguments are ignored and the specified build is terminated."
    },
    "captureTaskOutput": {
      "type": [
        "string",
        "null"
      ],
      "description": "The path of a specific task (e.g., ':app:dependencies') to capture and return output for exclusively. This is highly token-efficient as it filters out all non-task console noise. Output over 100 lines will be truncated; use 'inspect_build' for full logs."
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
      "description": "Additional advanced invocation arguments for the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




