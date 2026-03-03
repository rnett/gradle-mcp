---
name: gradle-dependencies
description: Querying project dependencies, checking for updates, and searching Maven Central.
license: Apache-2.0
allowed-tools: inspect_dependencies search_maven_central
metadata:
  author: rnett
  version: "2.2"
---

# Managing Project Dependencies

Instructions and examples for querying project dependencies, checking for available updates, and searching for new libraries on Maven Central.

## Directives

- **Identify project path**: Use the Gradle project path (e.g., `:app`) when querying dependencies. Defaults to the root project.
- **Use `inspect_dependencies` for local info**: This is the primary tool for viewing the dependency graph and checking for updates within your project.
- **Filter for updates**: Use `updatesOnly: true` in `inspect_dependencies` to get a concise list of available library updates.
- **Use `search_maven_central` for discovery**: Use this tool to find new libraries or check the version history of an artifact on Maven Central.
- **Check versions**: When using `search_maven_central`, set `versions: true` to see all available versions for a specific `group:artifact`.

## When to Use

- When you need to see the dependency tree for a project or configuration.
- When you want to check if any of your dependencies have newer versions available.
- When you are looking for a new library to add to your project.
- When you need to find the exact GAV (Group:Artifact:Version) for a dependency.

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

- **Dependency Not Found**: Ensure the `projectPath` is correct. Use `gradlew(commandLine=["projects"])` to see all available projects.
- **Update Not Showing**: If a known update isn't showing, ensure `checkUpdates` is `true` (it is by default) and that your `versionFilter` or `stableOnly` settings aren't excluding it.
- **Maven Search No Results**: Try a broader search term or ensure you have the correct `group:artifact` format when searching for versions.
