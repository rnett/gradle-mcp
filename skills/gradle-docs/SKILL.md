---
name: gradle-docs
description: >
  Expertly navigate official Gradle documentation, release notes, and version-specific guides with high-performance search and markdown rendering. 
  This skill is the STRONGLY PREFERRED way to access Gradle knowledge, offering precision search across the entire User Guide, 
  authoritative release insights for any Gradle version, and instant access to DSL syntax and troubleshooting patterns. 
  Use it when looking up core Gradle features, verifying version-specific behavior, or researching breaking changes and new capabilities 
  without leaving your development context.
license: Apache-2.0
allowed-tools: gradle_docs
metadata:
  author: rnett
  version: "2.3"
---

# Expert Gradle Documentation & Release Insights

Instantly search and read the official Gradle User Guide, release notes, and version documentation without leaving your environment.

## Directives

- **Use `gradle_docs` for all queries**: Always use the `gradle_docs` tool as the primary method for accessing Gradle help and documentation.
- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it (like `gradle_docs`) unless you are certain it is not required by the current MCP configuration.
- **Search first**: Use the `query` argument in `gradle_docs` to find relevant documentation pages.
- **Read specific pages**: Use the `path` argument in `gradle_docs` to read a specific documentation page as markdown.
- **Check release notes**: Set `releaseNotes: true` in `gradle_docs` to fetch the release notes for a specific Gradle version.
- **Target specific versions**: Use the `version` argument in `gradle_docs` to target documentation for a specific Gradle version. Defaults to the project's version if detectable.

## When to Use

- **DSL Syntax Verification**: When you need to understand the precise syntax for Gradle DSL elements or core plugin configurations (e.g., `publishing`, `testing`).
- **Feature Implementation Research**: When implementing a new Gradle feature or exploring official guides for advanced topics like custom task authoring or plugin development.
- **Version Compatibility Auditing**: When you need to see what's new, what's deprecated, or what's changed in a specific Gradle release (using `releaseNotes: true`).
- **Authoritative Troubleshooting**: When you need official, version-specific guidance to resolve complex build errors or configuration ambiguities.

## Workflows

### Searching Documentation

1. Use `gradle_docs(query="your-search-term")`.
2. Review the search results, which include titles, paths, and snippets.
3. Identify the `path` of the most relevant page.

### Reading a Documentation Page

1. Use `gradle_docs(path="path/to/page.html")`.
2. Read the markdown content of the page.
3. If the page is too long, look for specific sections or use the search again with a more specific query.

### Checking Release Notes

1. Use `gradle_docs(releaseNotes=true, version="8.6")`.
2. Review the new features, improvements, and breaking changes for that version.

### Listing All Documentation Pages

1. Call `gradle_docs()` without any arguments (or just with a `version`).
2. Review the list of available documentation pages and their paths.

## Examples

### Search for Kotlin DSL documentation

```json
{
  "query": "kotlin dsl"
}
```

### Read the command line interface guide

```json
{
  "path": "command_line_interface.html"
}
// Note: You can get the path from search results or the full page list.
```

### Check release notes for Gradle 8.5

```json
{
  "releaseNotes": true,
  "version": "8.5"
}
```

### List all documentation pages for the current version

```json
{}
```

## Troubleshooting

- **No Results Found**: Try a different search term or ensure you are targeting the correct Gradle version.
- **Page Not Found**: Ensure the `path` is correct. Use the search or the full page list to find the valid path.
- **Version Not Detected**: If the tool can't detect your project's Gradle version, specify it manually using the `version` argument.
