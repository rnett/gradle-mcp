---
name: gradle-introspection
description: Using Gradle's built-in introspection tasks and flags to query project structure, tasks, and environment.
license: Apache-2.0
allowed-tools: gradlew inspect_build
metadata:
  author: rnett
  version: "1.4"
---

# Gradle Introspection and Environment

Instructions and examples for using Gradle's built-in introspection tasks (`projects`, `tasks`, `help`, etc.) and global flags (`--version`, `--help`) using the Gradle MCP tools.

## Directives

- **Use `gradlew` for introspection**: Always use the `gradlew` tool to run introspection tasks or flags.
- **Use `captureTaskOutput` for clean results**: When running a specific introspection task (e.g., `:app:tasks`), set `captureTaskOutput` to that task path to get only the relevant output.
- **Use `inspect_build` for status**: Use the `inspect_build` tool to check if a build is currently running or to see the history of previous builds.
- **Identify project structure**: Use the `projects` task to see the multi-project structure and identify valid project paths for other tools.

## When to Use

- When you need to see the project's multi-module structure (`projects`).
- When you need to discover available tasks in a project (`tasks`).
- When you need detailed information about a specific task's options (`help --task <taskName>`).
- When you need to check the Gradle version, JVM version, and OS details (`--version`).
- When you need to see global Gradle command-line options (`--help`).
- When you need to inspect project properties or configurations.

## Workflows

### Running a Single Task for Clean Output

To run a task and get only its output (filtering out Gradle's startup and summary text), use the `captureTaskOutput` parameter in the `gradlew` tool.

1. Set `commandLine` to the task path (e.g., `[":app:myTask"]`).
2. Set `captureTaskOutput` to the same task path (e.g., `":app:myTask"`).
3. The tool will return only the output produced by that specific task.

### Using Diagnostic Tasks

Gradle provides several built-in tasks for inspecting the build. Always use `captureTaskOutput` with these for the best experience.

#### Project Structure and Tasks

- **`projects`**: Lists the sub-projects of the project.
    - Example: `gradlew(commandLine=[":projects"], captureTaskOutput=":projects")`
- **`tasks`**: Lists the tasks runnable from the project.
    - `--all`: Show additional tasks (including those not in a group) and detail.
    - `--group <groupName>`: Show tasks for a specific group (e.g., `build`, `help`, `verification`).
    - Example: `gradlew(commandLine=[":app:tasks", "--all"], captureTaskOutput=":app:tasks")`
- **`help`**: Displays help information.
    - `--task <taskName>`: Get detailed information for a specific task, including its options, type, and description.
    - Example: `gradlew(commandLine=[":help", "--task", "test"], captureTaskOutput=":help")`

#### Dependency Analysis

- **`dependencies`**: Lists all dependencies declared in the project.
    - `--configuration <name>`: Filter report to a specific configuration (e.g., `implementation`, `runtimeClasspath`).
    - *Note: For a better interactive experience, prefer the `inspect_dependencies` tool if available.*
    - Example: `gradlew(commandLine=[":app:dependencies", "--configuration", "runtimeClasspath"], captureTaskOutput=":app:dependencies")`
- **`dependencyInsight`**: Displays insight into a specific dependency.
    - `--dependency <gav-or-ga-or-project>`: The dependency to investigate (e.g., `slf4j-api`, `org.slf4j:slf4j-api:1.7.30`, or `:my-library`).
    - `--configuration <name>`: The configuration to look in.
    - Example: `gradlew(commandLine=[":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"], captureTaskOutput=":app:dependencyInsight")`
- **`buildEnvironment`**: Displays all buildscript dependencies (plugins and their dependencies) declared in the project.
    - Example: `gradlew(commandLine=[":buildEnvironment"], captureTaskOutput=":buildEnvironment")`

#### Advanced Configuration Analysis

- **`outgoingVariants`**: Displays the outgoing variants of the project (what it provides to other projects).
    - `--all`: Shows all variants, including legacy and deprecated ones.
    - `--variant <name>`: Report on a specific variant.
    - Example: `gradlew(commandLine=[":app:outgoingVariants"], captureTaskOutput=":app:outgoingVariants")`
- **`resolvableConfigurations`**: Displays the configurations that can be resolved in the project.
    - `--all`: Shows all resolvable configurations, including legacy and deprecated ones.
    - `--configuration <name>`: Report on a specific configuration.
    - Example: `gradlew(commandLine=[":app:resolvableConfigurations"], captureTaskOutput=":app:resolvableConfigurations")`
- **`artifactTransforms`**: Displays the Artifact Transforms that can be executed in the project.
    - Example: `gradlew(commandLine=[":artifactTransforms"], captureTaskOutput=":artifactTransforms")`

#### Project State and Environment

- **`properties`**: Lists all properties of the project.
    - `--property <name>`: Displays only the value of a specific property (greatly reduces verbosity).
    - Example: `gradlew(commandLine=[":properties", "--property", "version"], captureTaskOutput=":properties")`
- **`javaToolchains`**: Displays the detected Java toolchains.
    - Example: `gradlew(commandLine=[":javaToolchains"], captureTaskOutput=":javaToolchains")`

## Global Flags

- **`--version` (or `-v`)**: Shows Gradle version, JVM, and OS info.
- **`--help` (or `-h`)**: Shows global Gradle command-line help. This is for Gradle itself, not any tasks.

## Examples

### Check Gradle Version

```json
{
  "commandLine": [
    "--version"
  ]
}
```

### List all projects in the build

```json
{
  "commandLine": [
    "projects"
  ],
  "captureTaskOutput": ":projects"
}
```

### Investigate a specific dependency

```json
{
  "commandLine": [
    ":app:dependencyInsight",
    "--dependency",
    "slf4j-api",
    "--configuration",
    "runtimeClasspath"
  ],
  "captureTaskOutput": ":app:dependencyInsight"
}
```

### Get help for a specific task

```json
{
  "commandLine": [
    ":app:help",
    "--task",
    "test"
  ],
  "captureTaskOutput": ":app:help"
}
```

### Inspect a specific project property

```json
{
  "commandLine": [
    ":properties",
    "--property",
    "buildDir"
  ],
  "captureTaskOutput": ":properties"
}
```

## Troubleshooting

- **Task Not Found**: Ensure you are using the correct project path prefix (e.g., `:app:tasks` vs `tasks`). Use the `projects` task to verify paths.
- **Output Too Noisy**: Always use `captureTaskOutput` with the introspection task path to filter out Gradle's startup and summary text.
- **Ambiguous Task**: If a task name is ambiguous, Gradle will fail. Use the full path (e.g., `:subproject:mytask`) to be explicit.
