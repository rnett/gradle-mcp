[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## gradle_docs

Searches and reads official Gradle documentation (User Guide, DSL Reference, Release Notes) for the project's exact Gradle version; use instead of generic web searches.

### Documentation Tags
- `tag:userguide` — high-level concepts
- `tag:dsl` — property/method syntax
- `tag:release-notes` — breaking changes and new features
- `tag:best-practices` — performance and design recommendations
- `tag:javadoc` — API docs
- `tag:samples` — code examples

Call with no arguments to browse available sections. Use `tag:<tag> <term>` to scope searches. Use `path="."` to explore the file tree.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": [
        "string",
        "null"
      ],
      "description": "Search query. Use `tag:<section>` to scope (e.g., `tag:userguide`, `tag:dsl`)."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Read a specific doc page path (e.g., 'userguide/command_line_interface.md'). Overrides query."
    },
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "Targeting a specific Gradle version (e.g., '8.6'). Defaults to the detected project version."
    },
    "projectRoot": {
      "type": [
        "string",
        "null"
      ],
      "description": "Detecting the project's Gradle version automatically by providing the project root."
    },
    "pagination": {
      "type": "object",
      "required": [],
      "properties": {
        "offset": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        },
        "limit": {
          "type": "integer",
          "minimum": -2147483648,
          "maximum": 2147483647
        }
      },
      "description": "Pagination. offset = zero-based start index (default 0); limit = max items/lines to return."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




