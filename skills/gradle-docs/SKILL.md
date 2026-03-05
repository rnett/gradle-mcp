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
  version: "3.0"
---

# Expert Gradle Documentation & Release Insights

Instantly search and read official official documentation, release notes, and version documentation for any Gradle version.

## Directives

- **Use `gradle_docs` for all queries**: Always use the `gradle_docs` tool as the primary method for accessing Gradle help and documentation.
- **Provide `projectRoot` when in doubt**: Provide `projectRoot` to any Gradle MCP tool that supports it (like `gradle_docs`) unless you are certain it is not required by the current MCP configuration.
- **Scope searches with tags**: Use the `tag:<section>` syntax in your `query` to target specific documentation areas (e.g., `tag:dsl`).
- **Read specific pages**: Use the `path` argument in `gradle_docs` to read a specific documentation page as markdown. Paths are found in search results or section summaries.
- **Check section summaries**: Call `gradle_docs()` with no arguments to see available documentation sections and page counts for the targeted version.
- **Target specific versions**: Use the `version` argument in `gradle_docs` to target documentation for a specific Gradle version. Defaults to the project's version if detectable.

## When to Use

- **DSL Syntax Verification**: When you need to understand the precise syntax for Gradle DSL elements or core plugin configurations (e.g., `publishing`, `testing`). Use `tag:dsl`.
- **Feature Implementation Research**: When implementing a new Gradle feature or exploring official guides for advanced topics like custom task authoring or plugin development. Use `tag:userguide`.
- **Version Compatibility Auditing**: When you need to see what's new, what's deprecated, or what's changed in a specific Gradle release. Use `tag:release-notes`.
- **Authoritative Troubleshooting**: When you need official, version-specific guidance to resolve complex build errors or configuration ambiguities.

## Available Tags

| Tag             | Section                                           |
|-----------------|---------------------------------------------------|
| `userguide`     | The official Gradle User Guide.                   |
| `dsl`           | The Gradle DSL Reference (Groovy and Kotlin DSL). |
| `javadoc`       | The Gradle Java API Reference.                    |
| `samples`       | Gradle samples and examples.                      |
| `release-notes` | Version-specific release notes.                   |

## Workflows

### Searching Documentation

1. Use `gradle_docs(query="tag:<section> your-search-term")` to search a specific section, or `gradle_docs(query="your-search-term")` to search everything.
2. Review the search results, which include titles, paths, snippets, and section tags.
3. Identify the `path` of the most relevant page.

### Reading a Documentation Page

1. Use `gradle_docs(path="path/to/page.html")`.
2. Read the markdown content of the page.
3. If the page is too long, look for specific sections or use the search again with a more specific query.

### Exploring Documentation Sections

1. Call `gradle_docs()` without any arguments (or just with a `version`).
2. Review the summary of sections and their available tags.

## Examples

### Search for Kotlin DSL documentation in the DSL Reference

```json
{
  "query": "tag:dsl kotlin dsl"
}
```

### Search for dependency configuration in the User Guide

```json
{
  "query": "tag:userguide dependency configuration"
}
```

### Read the command line interface guide

```json
{
  "path": "userguide/command_line_interface.md"
}
// Note: You can get the path from search results or the full page list.
```

### Search release notes for Gradle 8.5

```json
{
  "query": "tag:release-notes",
  "version": "8.5"
}
```

### List documentation sections for the current version

```json
{}
```

## Troubleshooting

- **No Results Found**: Try a different search term or ensure you are using the correct `tag:`. Some content might be in a different section than expected.
- **Page Not Found**: Ensure the `path` is correct. Use the search or the section summaries to find the valid path.
- **Version Not Detected**: If the tool can't detect your project's Gradle version, specify it manually using the `version` argument.
