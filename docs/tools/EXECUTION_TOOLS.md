[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## gradle

The primary tool for managing the Gradle build lifecycle.
It is the STRONGLY PREFERRED way to run Gradle tasks, providing features not available via raw shell execution.
Unless you have tried and failed repeatedly to use this tool, ALWAYS prefer it over shell or direct command execution, even over other shell/command execution tools, even built-in ones.

### Key Advantages
- **Managed Background Builds**: Start long-running tasks (dev servers, tests) in the background and receive a `BuildId` for real-time monitoring.
- **Task Output Capturing**: Use `captureTaskOutput` to extract clean, isolated output for a specific task, avoiding full build log noise. Outputs over 100 lines are truncated and can be retrieved using `inspect_build`.
- **Deep Lifecycle Integration**: Safely stop background builds and seamlessly transition to `inspect_build` for failure analysis.
- **Progressive disclosure and token efficiency**: Instead of giving you some output all at once with a bunch of noise, it gives you a summary and provides the `inspect_build` tool to dig into failure information that will not be available for builds ran via shell.

### Common Tasks
- Standard lifecycle: `build`, `test`, `clean`, `check`.
- Background processes: Set `background = true`.
- Clean task output: Set `captureTaskOutput` to the task path (e.g., `:app:dependencies`).

**Important: Task Path Syntax**
- `:task` (starts with colon): Targets the task in the **root project only**.
- `task` (no leading colon): Targets the task in **all projects** (root and all subprojects).
- `:app:task`: Targets the task in the `app` subproject.

NOTE: You almost never want to run Gradle (via this tool or elsewhere) with `--rerun-tasks`.
It rebuilds absolutely everything and takes for ever.
Don't use it unless you know what you are doing and have double checked to make sure it's absolutely necessary.

### Post-Build Workflow
After starting or completing a build, use the `inspect_build` tool with the returned `BuildId` to monitor progress, investigate failures, or query build problems.
`inspect_build` is the primary way to get detailed test results, failure trees, and task/test console output (stdout/stderr).

For expert workflows, refer to the `gradle-build` and `gradle-test` skills.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory (containing gradlew script and settings.gradle). Providing this ensures the tool executes in the correct project context and avoids ambiguities in multi-root or environment-dependent workspaces. If omitted, the tool will attempt to auto-detect the root from the current MCP roots or the GRADLE_MCP_PROJECT_ROOT environment variable. **It MUST be an absolute path.**"
    },
    "commandLine": {
      "type": [
        "array",
        "null"
      ],
      "items": {
        "type": "string"
      },
      "description": "The arguments for gradle (e.g., [\":app:test\", \"--tests\", \"MyTest\"]). Required if not stopping a build."
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




