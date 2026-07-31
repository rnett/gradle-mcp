<!--
class: authored-local
skill: using-gradle
-->
# Dependency Inspection

Audits the resolved dependency graph, inspects version conflicts, and discovers resolution paths using Gradle MCP tools.

## Inspecting the Dependency Graph

### Full Dependency Report

Use `inspect_dependencies` to get the resolved dependency tree for a project:

```json
{
  "projectPath": ":app",
  "onlyDirect": false
}
```

Set `onlyDirect: true` to see only direct dependencies.

### Configuration-Scoped Inspection

```json
{
  "projectPath": ":app",
  "configuration": "runtimeClasspath"
}
```

Limit the report to a specific configuration.

### Targeted Dependency Filter

```json
{
  "projectPath": ":app",
  "dependency": "^org\\\\.jetbrains\\\\.kotlinx:kotlinx-coroutines-core(:.*)?$"
}
```

Use a Kotlin regex over `group:name:version[:variant]` to narrow the report.

## Version Conflict Resolution

### Using `dependencyInsight`

```json
{
  "commandLine": [":app:dependencyInsight", "--dependency", "slf4j-api", "--configuration", "compileClasspath"],
  "captureTaskOutput": ":app:dependencyInsight"
}
```

Shows the exact resolution path: which module requested which version, and which version won.

### Using `inspect_dependencies` with Targeted Filter

```json
{
  "projectPath": ":app",
  "dependency": "slf4j-api"
}
```

Shows all paths where the dependency appears in the resolved tree.

## Plugin Dependency Auditing

Inspect the buildscript classpath for plugins:

```json
{
  "sourceSetPath": ":buildscript"
}
```

For subproject plugins:

```json
{
  "sourceSetPath": ":app:buildscript"
}
```

## Resolution Strategies

Common conflict resolution approaches:

1. **Force a version**: Add `resolutionStrategy.force("group:artifact:version")` in the consuming project.
2. **Exclude transitive**: Use `exclude(group = "...", module = "...")` on the dependency declaration.
3. **Align versions**: Use a BOM or platform dependency to align transitive versions.
4. **Constraint**: Add a dependency constraint to enforce a minimum version.

For making these changes, hand off to `authoring-gradle-builds`.

## Examples

### Audit all dependencies of a module

```json
{ "projectPath": ":app", "onlyDirect": false }
```

### Find why a specific version was selected

```json
{
  "commandLine": [":app:dependencyInsight", "--dependency", "guava", "--configuration", "runtimeClasspath"],
  "captureTaskOutput": ":app:dependencyInsight"
}
```

### Check plugin classpath

```json
{ "sourceSetPath": ":buildscript", "onlyDirect": true }
```
