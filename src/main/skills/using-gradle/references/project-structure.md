<!--
class: authored-local
skill: using-gradle
-->
# Project Structure & Introspection

Maps the multi-project hierarchy, discovers runnable tasks, and inspects project properties using Gradle MCP tools.

## Mapping the Project Hierarchy

### List All Sub-Projects

```json
{
  "commandLine": [":projects"],
  "captureTaskOutput": ":projects"
}
```

Returns the complete multi-project tree with root and subproject names.

### Discover Runnable Tasks

```json
{
  "commandLine": [":app:tasks", "--all"],
  "captureTaskOutput": ":app:tasks"
}
```

Lists all tasks (including implicit) for a specific project. Omit `--all` for just the primary tasks.

### Task-Specific Documentation

```json
{
  "commandLine": [":help", "--task", "test"],
  "captureTaskOutput": ":help"
}
```

Returns authoritative documentation for any task, including its type, group, description, and available options.

## Property Inspection

### Surgical Property Extraction

```json
{
  "commandLine": [":properties", "--property", "version"],
  "captureTaskOutput": ":properties"
}
```

Extracts a single property value without the noise of listing all properties.

### Dependency Resolution Paths

```json
{
  "commandLine": [":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"],
  "captureTaskOutput": ":app:dependencyInsight"
}
```

Shows the exact resolution path for a specific dependency in a specific configuration.

## Authoritative Task Path Syntax

Gradle uses two ways to identify tasks from the command line:

### Task Selectors (Recursive Execution)

A task name **without a leading colon** (e.g., `test`, `build`) acts as a selector. Gradle executes that task in **every project** that contains a task with that name.

- `gradle(commandLine=["test"])` → executes `test` in all projects.

### Absolute Task Paths (Targeted Execution)

A task path **with a leading colon** targets a single specific project.

- `gradle(commandLine=[":test"])` → root project only.
- `gradle(commandLine=[":app:test"])` → `:app` subproject only.

## Examples

### Map a multi-project build

```json
{ "commandLine": [":projects"], "captureTaskOutput": ":projects" }
```

### Find all tasks in root project

```json
{ "commandLine": [":tasks", "--all"], "captureTaskOutput": ":tasks" }
```

### Get help for a specific task

```json
{ "commandLine": [":help", "--task", "compileKotlin"], "captureTaskOutput": ":help" }
```
