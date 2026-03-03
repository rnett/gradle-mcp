[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## gradle_docs

Search and read the Gradle User Guide, release notes, and version documentation. `releaseNotes` takes precedence over `path`, which takes precedence over `query` - only one will be used.

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
      "description": "Search query for the documentation."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "Specific documentation page path to read. If not set, a list of all pages will be returned."
    },
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "Specific Gradle version documentation to target. Defaults to project version. Uses the latest if no project root is available."
    },
    "releaseNotes": {
      "type": "boolean",
      "description": "If true, fetch the release notes for the version"
    },
    "projectRoot": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




