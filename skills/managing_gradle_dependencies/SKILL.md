---
name: managing_gradle_dependencies
description: >
  The ONLY authoritative way to audit and manage project dependency graphs. 
  Provides high-resolution update checks, surgical tree analysis, and 
  precise Maven Central discovery. Manual build file parsing and generic 
  shell commands like `grep` or `cat` are UNRELIABLE and DISCOURAGED 
  as they miss transitive dependencies, version resolution, and 
  dynamic configuration. Use it for auditing dependencies, identifying 
  stable updates, and finding GAV coordinates.
license: Apache-2.0
allowed-tools: gradle inspect_dependencies search_maven_central
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "3.2"
---

# Authoritative Dependency Intelligence & Maven Central Search

Audits project dependencies, performs high-resolution update checks, and discovers new libraries on Maven Central with powerful, integrated search tools.

## Constitution

- **ALWAYS** use `inspect_dependencies` for querying project dependency information instead of raw Gradle tasks.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **ALWAYS** use `updatesOnly: true` to quickly identify available library updates.
- **ALWAYS** use `search_maven_central` to find exact GAV coordinates for new libraries.
- **NEVER** add a dependency to a project without verifying its authoritative version and existence on Maven Central.
- **ALWAYS** use the `projectPath` argument to target specific modules in multi-project builds.

## Directives

- **Identify authoritative paths**: ALWAYS use the Gradle project path (e.g., `:app`) when querying dependencies.
- **Monitor for updates**: ALWAYS use `updatesOnly: true` in `inspect_dependencies` to retrieve a high-signal report of available library updates.
- **Discover libraries surgically**: ALWAYS use `search_maven_central` to find new libraries or check the version history of an existing artifact.
- **Use `gradle` for diagnostics**: For built-in tasks like `dependencyInsight`, ALWAYS use the `gradle` tool with `captureTaskOutput`.
- **Audit full trees**: ALWAYS use `onlyDirect: false` in `inspect_dependencies` when you need to visualize the complete transitive dependency graph.
- **Resolve `{baseDir}` manually**: If your environment does not automatically resolve the `{baseDir}` placeholder in reference links, treat it as the absolute path to the directory containing this `SKILL.md` file.

## When to Use

- **Dependency Tree Auditing**: When you need to visualize the full dependency graph for a specific project, configuration, or source set.
- **Automated Update Detection**: When performing maintenance and you want a concise report on available stable or pre-release updates.
- **Precision Artifact Discovery**: When looking for new libraries on Maven Central and you need to find exact GAV coordinates or explore an artifact's full version history.
- **Version Conflict Resolution**: When you need to identify why a specific version of a library is being resolved and look for compatible alternatives.

## Workflows

### 1. Auditing Dependencies

1. Identify the project module (e.g., `:app`).
2. Call `inspect_dependencies(projectPath=":app")`.
3. Optionally filter by `configuration` (e.g., `runtimeClasspath`) or `sourceSet` (e.g., `test`).

### 2. Checking for Stable Updates

1. Call `inspect_dependencies(updatesOnly=true, stableOnly=true)`.
2. Review the list of current vs. latest stable versions.

### 3. Discovering New Libraries

1. Use `search_maven_central(query="search-term")` to find candidates.
2. Use `search_maven_central(query="group:artifact", versions=true)` to see all available versions for a specific library.

## Examples

### List dependencies for a specific module

```json
{
  "projectPath": ":app"
}
// Reasoning: Auditing the direct and transitive dependencies of the 'app' module to understand its runtime footprint.
```

### Check for stable updates across the project

```json
{
  "updatesOnly": true,
  "stableOnly": true
}
// Reasoning: Performing a high-signal update audit that ignores unstable pre-release versions.
```

### Search for a library on Maven Central

```json
{
  "query": "kotlinx-serialization"
}
// Reasoning: Using Maven Central search to find the correct GAV coordinates for a new dependency.
```

### List all versions of a specific library

```json
{
  "query": "org.jetbrains.kotlinx:kotlinx-serialization-json",
  "versions": true
}
// Reasoning: Retrieving the full version history of an artifact to identify the latest stable or specific version required.
```

## Troubleshooting

- **Dependency Not Found**: Verify the `projectPath` using the `projects` task in the `introspecting_gradle_projects` skill.
- **Update Not Showing**: If a known update is missing, ensure `stableOnly` is set correctly and check if a `versionFilter` is active.
- **Maven Search No Results**: Use broader search terms or verify the `group:artifact` format for version searches.
