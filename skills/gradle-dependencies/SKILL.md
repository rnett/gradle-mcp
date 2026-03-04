---
name: gradle-dependencies
description: >
  Authoritatively manage your project's dependency graph, perform high-resolution update checks, 
  and discover new libraries with precision Maven Central search. 
  This skill is the STRONGLY PREFERRED way to handle dependency intelligence, offering surgical insights 
  into your dependency tree, automated version update detection, and seamless integration with Maven Central 
  for artifact discovery. Use it for auditing project dependencies, identifying stable update paths, 
  or searching for the exact Group:Artifact:Version (GAV) of a new library without leaving your development environment.
license: Apache-2.0
allowed-tools: gradle inspect_dependencies search_maven_central
metadata:
  author: rnett
  version: "2.4"
---

# Comprehensive Dependency Intelligence & Maven Central Search

Easily query project dependencies, perform authoritative update checks, and discover new libraries on Maven Central with powerful, integrated search tools.

## Directives

- **Identify project path**: Use the Gradle project path (e.g., `:app`) when querying dependencies. Defaults to the root project.
- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it unless you are certain it is not required by the current MCP configuration.
- **Use `inspect_dependencies` for local info**: This is the primary tool for viewing the dependency graph and checking for updates within your project.
- **Filter for updates**: Use `updatesOnly: true` in the `inspect_dependencies` tool to get a concise list of available library updates.
- **Use `search_maven_central` for discovery**: Use the `search_maven_central` tool to find new libraries or check the version history of an artifact on Maven Central.
- **Use `gradle` for introspection**: For built-in Gradle dependency tasks like `dependencies` or `dependencyInsight`, use the `gradle` tool with `captureTaskOutput`. See the `gradle-introspection` skill for details.
- **Check versions**: When using `search_maven_central`, set `versions: true` to see all available versions for a specific `group:artifact`.

## When to Use

- **Dependency Tree Auditing**: When you need to visualize the full dependency graph for a project, configuration, or source set to understand transitives and version resolutions.
- **Automated Update Detection**: When performing regular build maintenance and you want a high-signal report on available stable or pre-release updates.
- **Precision Artifact Discovery**: When looking for new libraries on Maven Central and you need to find exact GAV coordinates or explore an artifact's full version history.
- **Version Conflict Resolution**: When you need to identify why a specific version of a library is being pulled in and look for more recent compatible alternatives.

## Workflows

### Viewing Dependencies

1. Use `inspect_dependencies` with the `projectPath` (e.g., `:app`).
2. Optionally specify a `configuration` (e.g., `runtimeClasspath`) or `sourceSet` (e.g., `test`).
3. Review the tree-like output. Note that `(*)` indicates a dependency already listed elsewhere.

### Checking for Updates

1. Use `inspect_dependencies(updatesOnly=true)`.
2. To ignore unstable versions, set `stableOnly: true`.
3. Review the list of current vs. latest versions.

### Searching for New Libraries

1. Use `search_maven_central(query="your-search-term")`.
2. Identify the `groupId` and `artifactId` from the results.
3. To see all versions for a library, use `search_maven_central(query="group:artifact", versions=true)`.

## Examples

### List dependencies for the app module

```json
{
  "projectPath": ":app"
}
```

### Check for stable updates only

```json
{
  "updatesOnly": true,
  "stableOnly": true
}
```

### Search for a library on Maven Central

```json
{
  "query": "kotlinx-serialization"
}
```

### List all versions of a specific library

```json
{
  "query": "org.jetbrains.kotlinx:kotlinx-serialization-json",
  "versions": true
}
```

## Troubleshooting

- **Dependency Not Found**: Ensure the `projectPath` is correct. Use `gradle(commandLine=["projects"])` to see all available projects.
- **Update Not Showing**: If a known update isn't showing, ensure `checkUpdates` is `true` (it is by default) and that your `versionFilter` or `stableOnly` settings aren't excluding it.
- **Maven Search No Results**: Try a broader search term or ensure you have the correct `group:artifact` format when searching for versions.
