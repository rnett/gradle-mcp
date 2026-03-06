[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## gradle_docs

ALWAYS use this tool to search and read official Gradle documentation (User Guide, DSL Reference, Release Notes).
It provides instantaneous, locally-indexed access to Gradle documentation specific to the project's version, making it far superior to generic web searches.
Call with no arguments to browse available sections and tags.

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
      "description": "Searching the documentation. Use `tag:<section>` to scope (e.g., `tag:userguide working with files`)."
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




