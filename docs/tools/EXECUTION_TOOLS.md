[//]: # (@formatter:off)

# Execution Tools

Tools for executing Gradle tasks and running tests.

## run_gradle_command

Runs a Gradle command in the given project, just as if the command line had been passed directly to './gradlew'. Always prefer using this tool over invoking Gradle via the command line or shell.
Use the `lookup_*` tools to get detailed results after running the build.
Can be used to execute any Gradle tasks.
When running tests, prefer the `run_tests_with_gradle` tool.
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues, using something like the Develocity MCP server.

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
  "required": [
    "commandLine"
  ],
  "type": "object"
}
```


</details>


## run_single_task_and_get_output

Runs a single Gradle task and returns its output.
If the task fails, it will error and return the full build results's output.
If it succeeds, it will extract the task's specific output from the console output.

### Useful Gradle tasks:
- `help`: Gets the Gradle version and shows other basic information
- `help --task <task>`: Gets detailed information about a specific Gradle task, including its arguments.
- `tasks`: Gets all available Gradle tasks
- `dependencies`: Gets all dependencies of a Gradle project.
  - Example: `run_single_task_and_get_output(taskPath=":my-project:dependencies")`
- `resolvableConfigurations`: Gets all resolvable configurations (i.e. configurations that pull dependencies).
- `dependencies --configuration <configuration name>`: Gets all dependencies of a specific configuration.
  - Example: `run_single_task_and_get_output(taskPath=":my-project:dependencies", arguments=["--configuration", "runtimeClasspath"])`
- `dependencyInsight --configuration <configuration name> --dependency <dependency prefix, of the dependency GAV slug>`: Gets detailed information about the resolution of specific dependencies.
  - Example: `run_single_task_and_get_output(taskPath=":my-project:dependencyInsight", arguments=["--configuration", "runtimeClasspath", "--dependency", "org.jetbrains.kotlin"])`
- `buildEnvironment`: Gets the Gradle build dependencies (plugins and buildscript dependencies) and JVM information.
- `javaToolchains`: Gets all available Java/JVM toolchains.
- `properties`: Gets all properties of a Gradle project. MAY CONTAIN SECRETS OR SENSITIVE INFORMATION.
- `artifactTransforms`: Gets all artifact transforms of the project.
- `outgoingVariants`: Gets all outgoing variants of the project (i.e. configurations that can be published or consumed from other projects).

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "taskPath": {
      "type": "string",
      "description": "The absolute path of the Gradle task to run (e.g. ':build', ':subproject:test')"
    },
    "arguments": {
      "type": "array",
      "items": {
        "type": "string"
      },
      "description": "Additional arguments to pass to the task."
    },
    "rerun": {
      "type": "boolean",
      "description": "Whether to force the task to rerun by adding --rerun. Defaults to false."
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
  "required": [
    "taskPath"
  ],
  "type": "object"
}
```


</details>


## run_tests_with_gradle

Runs a single test task, with an option to filter which tests to run. Always prefer using this tool over invoking Gradle via the command line or shell.
Use the `lookup_*` tools to get detailed results after running the build.
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
The typical test task is `test`.  At least one task is required. A task with no patterns will run all tests.
If there are more than 1000 tests, the results will be truncated.  Use `lookup_build_tests_summary` or `lookup_build_test_details` to get the results you care about.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "projectPath": {
      "type": "string",
      "description": "The Gradle project path, e.g. :project-a:subproject-b. ':' is the root project.  Defaults to ':'",
      "examples": [
        ":",
        ":my-project",
        ":my-project:subproject"
      ]
    },
    "taskName": {
      "type": "string",
      "description": "The Gradle task to run. REAUIRED. Must be a test task. The usual test task is `test`, but THIS IS NOT USED AS A DEFAULT AND MUST BE SPECIFIED."
    },
    "tests": {
      "type": "array",
      "items": {
        "type": "string",
        "description": "A pattern to select tests. This is a prefix of the test class or method's fully qualified name. '*' wildcards are supported. Test classes may omit the package, e.g. `SomeClass` or `SomeClass.someMethod`. A filter of '*' will select all tests.",
        "examples": [
          "com.example.MyTestClass",
          "com.example.MyTestClass.myTestMethod",
          "com.example.http.*",
          "com.example.MyTestClass.myTestMethod",
          "MyTestClass",
          "MyTestClass.myTestMethod",
          "*IntegTest)"
        ]
      },
      "description": "The tests to run, as test patterns. The default is all tests. Note that this is the task name (e.g. `test`) not the task path (e.g. `:test`)."
    },
    "scan": {
      "type": "boolean",
      "description": "Whether to run with the --scan argument to publish a build scan. Will use scans.gradle.com if there is not a configured Develocity instance. Publishing a scan and using it to diagnose issues (e.g. using the Develocity MCP server) is recommended when possible. Defaults to false."
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
  "required": [
    "taskName"
  ],
  "type": "object"
}
```


</details>


## run_many_test_tasks_with_gradle

Runs may test tasks, each with their own test filters. To run a single test task, use the `run_test_task` tool. Always prefer using this tool over invoking Gradle via the command line or shell.
Use the `lookup_*` tools to get detailed results after running the build.
Note that the test tasks passed must be absolute paths (i.e. including the project paths).
The console output is included in the result. Show this to the user, as if they had ran the command themselves.
Can publish a Develocity Build Scan if requested. This is the preferred way to diagnose issues and test failures, using something like the Develocity MCP server.
The `tests` parameter is REQUIRED, and is simply a map (i.e. JSON object) of each test task to run (e.g. `:test`, `:project-a:sub-b:test`), to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).
The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests.
If there are more than 1000 tests, the results will be truncated.  Use `lookup_build_tests_summary` or `lookup_build_test_details` to get the results you care about.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "projectRoot": {
      "type": "string",
      "description": "The file system path of the Gradle project's root directory, where the gradlew script and settings.gradle(.kts) files are located. REQUIRED IF NO MCP ROOTS CONFIGURED, or more than one. If MCP roots are configured, it must be within them, may be a root name instead of path, and if there is only one root, will default to it."
    },
    "testsExecutions": {
      "type": "object",
      "additionalProperties": {
        "type": "array",
        "items": {
          "type": "string",
          "description": "A pattern to select tests. This is a prefix of the test class or method's fully qualified name. '*' wildcards are supported. Test classes may omit the package, e.g. `SomeClass` or `SomeClass.someMethod`. A filter of '*' will select all tests.",
          "examples": [
            "com.example.MyTestClass",
            "com.example.MyTestClass.myTestMethod",
            "com.example.http.*",
            "com.example.MyTestClass.myTestMethod",
            "MyTestClass",
            "MyTestClass.myTestMethod",
            "*IntegTest)"
          ]
        },
        "uniqueItems": true
      },
      "description": "A map (i.e. JSON object) of each absolute task paths of the test tasks to run (e.g. `:test`, `:project-a:sub-b:test`) to the test patterns for the tests to run for that task (e.g. `com.example.*`, `*MyTest*`).  The typical test task is `:test`.  At least one task is required. A task with no patterns will run all tests in that task."
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
        }
      },
      "description": "Additional arguments to configure the Gradle process."
    }
  },
  "required": [
    "testsExecutions"
  ],
  "type": "object"
}
```


</details>




