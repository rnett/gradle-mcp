---
name: gradle-docs
description: Searching and reading the Gradle User Guide, release notes, and version documentation.
license: Apache-2.0
allowed-tools: gradle_docs
metadata:
  author: rnett
  version: "2.1"
---

# Accessing Gradle Documentation

Instructions and examples for searching and reading the Gradle User Guide, release notes, and version-specific documentation using the consolidated `gradle_docs` tool.

## Directives

- **Use `gradle_docs` for all queries**: This is the primary tool for accessing Gradle help and documentation.
- **Search first**: Use the `query` argument to find relevant documentation pages.
- **Read specific pages**: Use the `path` argument to read a specific documentation page as markdown.
- **Check release notes**: Set `releaseNotes: true` to fetch the release notes for a specific Gradle version.
- **Target specific versions**: Use the `version` argument to target documentation for a specific Gradle version. Defaults to the project's version if detectable.

## When to Use

- When you need to understand how to use a specific Gradle feature or plugin.
- When you are looking for the correct syntax for a Gradle DSL element.
- When you want to see what's new in a specific Gradle release.
- When you need to troubleshoot a Gradle-related issue and need official guidance.

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
