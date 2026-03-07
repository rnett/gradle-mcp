[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## gradle_docs

ALWAYS use this tool to search and read official Gradle documentation instead of generic web searches.
This provides instantaneous, locally-indexed access to the authoritative User Guide, DSL Reference, and Release Notes specific to YOUR project's exact Gradle version.

### Common Documentation Tags

- **`tag:userguide`**: The official Gradle User Guide (high-level concepts).
- **`tag:dsl`**: The Gradle DSL Reference (exact property/method syntax).
- **`tag:release-notes`**: Version-specific breaking changes and new features.
- **`tag:best-practices`**: Official recommendations for performance and design.
- **`tag:javadoc`**: Detailed technical API documentation.
- **`tag:samples`**: Official code examples.

### Research Best Practices

1.  **Browse First**: Call with no arguments to see available sections and page counts.
2.  **Scope Surgically**: Use `tag:<tag> <term>` in the `query` to narrow results.
3.  **Read Recursively**: Use `path="."` or `path=""` to explore the documentation file tree.
4.  **Target Versions**: Use `version="8.6"` to check behavior in different Gradle releases.

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
      "description": "Searching the documentation. Use `tag:<section>` to scope (e.g., `tag:userguide`, `tag:best-practices` for official recommendations)."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Reading a specific documentation page or asset path (e.g., 'userguide/command_line_interface.md'). Takes precedence over query."
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
      "description": "Pagination parameters. Offset is the zero-based starting index (defaults to 0). Limit is the maximum number of items/lines to return."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




