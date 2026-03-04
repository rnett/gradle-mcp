---
name: gradle-introspection
description: >
  Authoritatively uncover the full structure of any Gradle project, discover available tasks, 
  and inspect the build environment with high-precision diagnostic tools. 
  This skill is the STRONGLY PREFERRED way to map project modules and configurations, 
  offering surgical visibility into multi-project structures, task-specific help, 
  and detailed environment reports. Use it for exploring complex module hierarchies, 
  identifying runnable tasks and their options, or performing deep-dive analysis 
  of project properties and artifact variants that are often obscured in raw build files.
license: Apache-2.0
allowed-tools: gradle inspect_build inspect_dependencies
metadata:
  author: rnett
  version: "1.6"
---

# Deep Project Structure & Environment Introspection

Map out project modules, discover all available tasks, and gain total visibility into your build configuration and environment using core Gradle diagnostic tools.

## Directives

- **Use `gradle` for introspection**: Always use the `gradle` tool to run introspection tasks or flags.
- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it (like `gradle`) unless you are certain it is not required by the current MCP configuration.
- **Use `captureTaskOutput` for clean results**: When running a specific introspection task (e.g., `:app:tasks`), set `captureTaskOutput` to that task path to get only the relevant output.
- **Use `inspect_build` for status**: Use the `inspect_build` tool to check if a build is currently running or to see the history of previous builds.
- **Identify project structure**: Use the `projects` task to see the multi-project structure and identify valid project paths for other tools.

## Authoritative Task Path Syntax

When using introspection tasks, understanding how Gradle resolves paths is essential for mapping the project correctly.

### 1. Task Selectors (Search ALL Projects)

When you run a task **without a leading colon** (e.g., `tasks`, `projects`), Gradle searches for that task name in the current project and all subprojects.

- **Example**: `gradle(commandLine=["tasks"])` -> Lists tasks for **every** project in the build. This is usually very noisy.

### 2. Absolute Task Paths (Target ONE Project)

To inspect a **single specific project**, always use a leading colon.

- **Root Project**: `gradle(commandLine=[":tasks"], captureTaskOutput=":tasks")`
- **Subproject**: `gradle(commandLine=[":app:tasks"], captureTaskOutput=":app:tasks")`

## When to Use

- **Multi-Module Project Mapping**: When you need to visualize the full project hierarchy and identify correct project paths for targeted task execution.
- **Task Discovery and Exploration**: When you need to find runnable tasks within a project or get authoritative help on a specific task's options and configuration.
- **Environment & Runtime Auditing**: When you need to verify the Gradle version, JVM toolchains, or OS details to troubleshoot environment-specific build issues.
- **High-Resolution Property Inspection**: When you need to extract specific project properties or variants to understand how the build is configured at runtime.
- **Dependency and Variant Analysis**: When performing deep-dive analysis of configurations, resolvable variants, or artifact transforms using Gradle's built-in diagnostics.

## Workflows

### Running a Single Task for Clean Output

To run a task and get only its output (filtering out Gradle's startup and summary text), use the `captureTaskOutput` parameter in the `gradle` tool.

1. Set `commandLine` to the task path (e.g., `[":app:myTask"]`).
2. Set `captureTaskOutput` to the same task path (e.g., `":app:myTask"`).
3. The tool will return only the output produced by that specific task.

### Using Diagnostic Tasks

Gradle provides several built-in tasks for inspecting the build. Always use `captureTaskOutput` with these for the best experience.

#### Project Structure and Tasks

- **`projects`**: Lists the sub-projects of the project.
    - Example: `gradle(commandLine=[":projects"], captureTaskOutput=":projects")`
- **`tasks`**: Lists the tasks runnable from the project.
    - `--all`: Show additional tasks (including those not in a group) and detail.
    - `--group <groupName>`: Show tasks for a specific group (e.g., `build`, `help`, `verification`).
  - Example: `gradle(commandLine=[":app:tasks", "--all"], captureTaskOutput=":app:tasks")`
- **`help`**: Displays help information.
    - `--task <taskName>`: Get detailed information for a specific task, including its options, type, and description.
  - Example: `gradle(commandLine=[":help", "--task", "test"], captureTaskOutput=":help")`

#### Dependency Analysis

- **`dependencies`**: Lists all dependencies declared in the project.
    - `--configuration <name>`: Filter report to a specific configuration (e.g., `implementation`, `runtimeClasspath`).
    - *Note: For a better interactive experience, prefer the `inspect_dependencies` tool if available.*
  - Example: `gradle(commandLine=[":app:dependencies", "--configuration", "runtimeClasspath"], captureTaskOutput=":app:dependencies")`
- **`dependencyInsight`**: Displays insight into a specific dependency.
    - `--dependency <gav-or-ga-or-project>`: The dependency to investigate (e.g., `slf4j-api`, `org.slf4j:slf4j-api:1.7.30`, or `:my-library`).
    - `--configuration <name>`: The configuration to look in.
  - Example: `gradle(commandLine=[":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"], captureTaskOutput=":app:dependencyInsight")`
- **`buildEnvironment`**: Displays all buildscript dependencies (plugins and their dependencies) declared in the project.
    - Example: `gradle(commandLine=[":buildEnvironment"], captureTaskOutput=":buildEnvironment")`

#### Advanced Configuration Analysis

- **`outgoingVariants`**: Displays the outgoing variants of the project (what it provides to other projects).
    - `--all`: Shows all variants, including legacy and deprecated ones.
    - `--variant <name>`: Report on a specific variant.
  - Example: `gradle(commandLine=[":app:outgoingVariants"], captureTaskOutput=":app:outgoingVariants")`
- **`resolvableConfigurations`**: Displays the configurations that can be resolved in the project.
    - `--all`: Shows all resolvable configurations, including legacy and deprecated ones.
    - `--configuration <name>`: Report on a specific configuration.
  - Example: `gradle(commandLine=[":app:resolvableConfigurations"], captureTaskOutput=":app:resolvableConfigurations")`
- **`artifactTransforms`**: Displays the Artifact Transforms that can be executed in the project.
    - Example: `gradle(commandLine=[":artifactTransforms"], captureTaskOutput=":artifactTransforms")`

#### Project State and Environment

- **`properties`**: Lists all properties of the project.
    - `--property <name>`: Displays only the value of a specific property (greatly reduces verbosity).
  - Example: `gradle(commandLine=[":properties", "--property", "version"], captureTaskOutput=":properties")`
- **`javaToolchains`**: Displays the detected Java toolchains.
    - Example: `gradle(commandLine=[":javaToolchains"], captureTaskOutput=":javaToolchains")`

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
