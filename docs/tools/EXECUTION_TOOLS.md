[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## gradle

Executes Gradle builds and tasks with background orchestration, task output capturing, and progressive feedback; ALWAYS use instead of raw shell `./gradlew`.

### Task Execution
- **Foreground** (default): STRONGLY PREFERRED; provides progressive output.
- **Background** (`background=true`): Use only for persistent tasks (servers) or parallel work.
- **Task Output Capturing** (`captureTaskOutput=":path:to:task"`): Returns clean task-specific output.
   - **DO NOT use Task Output Capturing for tests**: Use `inspect_build` with `testName` and `mode="details"`.

After starting a build, use `inspect_build` with the returned `BuildId` to monitor progress or diagnose failures.
Note: Prefer `--rerun` (single task) over `--rerun-tasks` (all tasks, even included builds). Use `invocationArguments: { envSource: "SHELL" }` if env vars (e.g., JDKs) aren't found.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "Absolute path to Gradle project root. Auto-detected from MCP roots or GRADLE_MCP_PROJECT_ROOT when present, must be specified otherwise (usually)."
    },
    "commandLine": {
      "type": [
        "array",
        "null"
      ],
      "items": {
        "type": "string"
      },
      "description": "Gradle CLI args. ':task' (root), 'task' (all projects), ':app:task'. Required if not stopping."
    },
    "background": {
      "type": "boolean",
      "description": "Start build in background; returns BuildId. Use only for servers or parallel work."
    },
    "stopBuildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "Stop an active background build by BuildId; all other args are ignored."
    },
    "captureTaskOutput": {
      "type": [
        "string",
        "null"
      ],
      "description": "Isolated output for a specific task path. DO NOT use for tests; use inspect_build with testName."
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
          "description": "Where to get the environment variables from to pass to Gradle. Defaults to INHERIT. SHELL starts a new shell process and queries its env vars. Recommended if Gradle isn't finding environment variables (e.g. for JDKs) that should be present, which can happen if the host process starts before the shell environment is fully loaded.",
          "type": "string"
        },
        "javaHome": {
          "type": [
            "string",
            "null"
          ],
          "description": "The path to the Java home directory to use for the Gradle process. Optional. If omitted, JAVA_HOME from the environment (see envSource) is used as a fallback."
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




