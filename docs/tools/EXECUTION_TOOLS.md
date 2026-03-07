[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## gradle

ALWAYS use this tool to execute Gradle builds, tasks, and tests instead of raw shell commands.
Direct shell execution of `./gradlew` is unreliable for AI agents because it produces interleaved, non-deterministic console output that is difficult to parse and lacks structured failure diagnostics.
This tool provides a managed environment with high-resolution feedback, authoritative background orchestration, and surgical task output capturing (`captureTaskOutput`), which is vastly superior and more token-efficient than parsing raw logs.
For deep diagnostics after any build, ALWAYS use `inspect_build` with the returned `BuildId` to access exhaustive test failures, stack traces, and console tails.
To investigate individual test failures, ALWAYS use `inspect_build` with `testName` and `mode="details"`. DO NOT use `captureTaskOutput` for tests.
Note: Recommend using `invocationArguments: { envSource: "SHELL" }` if Gradle isn't finding environment variables (e.g. for JDKs) that should be present. This is because some hosts start before the shell environment is fully loaded.
Note: Avoid `--rerun-tasks` unless investigating cache issues.

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
      "description": "The arguments for gradle. Syntax: ':task' (root only), 'task' (all projects), or ':app:task'. Required if not stopping a build."
    },
    "background": {
      "type": "boolean",
      "description": "Setting to true starts the build in the background and returns a managed BuildId immediately. Use ONLY for persistent tasks (e.g., servers) or when you explicitly intend to perform other tasks in parallel. Foreground is STRONGLY PREFERRED for most tasks as it provides superior progressive disclosure."
    },
    "stopBuildId": {
      "type": [
        "string",
        "null"
      ],
      "description": "Terminating an active background build by providing its BuildId. If provided, all other arguments are ignored."
    },
    "captureTaskOutput": {
      "type": [
        "string",
        "null"
      ],
      "description": "Capturing and returning output for a specific task path (e.g., ':app:dependencies') exclusively. This is highly token-efficient as it eliminates all non-task console noise. Output over 100 lines will be truncated; use `inspect_build` for full logs. DO NOT use this for tests; ALWAYS use `inspect_build` with `testName` and `mode=\"details\"` for isolated, untruncated individual test output and stack traces."
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
          "description": "Where to get the environment variables from to pass to Gradle. Defaults to INHERIT. SHELL starts a new shell process and queries its env vars. Recommended if Gradle isn't finding environment variables (e.g. for JDKs) that should be present, which can happen if the host process starts before the shell environment is fully loaded."
        }
      },
      "description": "Applying additional advanced invocation arguments for the Gradle process."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




