---
name: gradle-docs
description: Searching and reading the Gradle User Guide documentation.
license: Apache-2.0
allowed-tools: get_all_gradle_docs_pages get_gradle_docs_page search_gradle_docs
metadata:
  author: rnett
  version: "1.0"
---

# Querying Gradle Documentation

Instructions and examples for searching and reading the Gradle User Guide.

## Directives

- **Prefer documentation over guessing**: If you are unsure about how a Gradle feature works or the correct syntax for a DSL element, use these tools to consult the official Gradle documentation.
- **Autodetect version**: The tools will automatically attempt to detect the Gradle version from the project root. THIS ONLY WORKS IF YOU HAVE SET THE MCP ROOT, and your agent supports it. When it doubt, look up and provide a version (e.g.
  by using the get task output tool with `--version`). You only need to provide a `version` if you want to consult documentation for a different version.
- **Paths are relative**: Documentation page paths are relative to the User Guide base URL (`https://docs.gradle.org/{version}/userguide/`).
- **Use search first**: If you don't know the exact page path, use `search_gradle_docs` to find relevant pages and their paths.
- **Read full pages for context**: When a search snippet isn't enough, use `get_gradle_docs_page` with the path found in the search results to read the full content as Markdown.

## When to Use

- When you need to understand a Gradle concept (e.g., "Task configuration avoidance", "Configuration cache").
- When you need to find the correct DSL syntax for a plugin or a built-in task.
- When you need to see examples of how to use a specific Gradle feature.
- When you want to see what changed in a specific Gradle version.

## Workflows

### Finding and Reading Documentation

1. **Search**: Use `search_gradle_docs` with a query (e.g., `query="task dependencies"`).
2. **Review Results**: Look at the titles and snippets to find the most relevant page. Note its `path` (e.g., `tutorial_using_tasks.html`).
3. **Read Page**: Use `get_gradle_docs_page` with the `path` to get the full content of the page.

### Listing All Available Pages

If you want to see the overall structure of the User Guide or find a page by title:

1. Use `get_all_gradle_docs_pages`.
2. Browse the list of titles and paths.

## Examples

### Search for configuration cache info

```json
{
  "query": "configuration cache"
}
```

### Read the "Gradle Basics" page

```json
{
  "path": "gradle_basics.html"
}
```

### List pages for a specific version

```json
{
  "version": "8.5"
}
```

### Search for info in a specific project root

```json
{
  "query": "build cache",
  "projectRoot": "."
}
```

## Troubleshooting

- **Page Not Found**: Ensure the path is relative to the `userguide/` directory and ends with `.html`. If you copied a URL from the web, only use the filename part.
- **No Results**: Try a broader search query or check if you are searching the correct Gradle version.
- **Version Detection Failed**: If the tool cannot find a `gradle-wrapper.properties` file, it will default to `current`. You can explicitly provide the `projectRoot` or a `version`.
