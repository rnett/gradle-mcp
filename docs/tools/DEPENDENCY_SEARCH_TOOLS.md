[//]: # (@formatter:off)

# Dependency Search Tools

Tools for querying maven repositories for dependency information.

## search_maven_central

ALWAYS use this tool to search Maven Central for library coordinates and version histories instead of relying on hallucinated versions or web searches.
It provides direct, paginated access to the authoritative artifact repository. Set `versions=true` and provide a `group:artifact` query to list all released versions.
Once identified, use `inspect_dependencies` to check if the project already uses the library.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": "string",
      "description": "Searching for artifacts by name, group, or coordinates. If `versions=true`, MUST be exactly 'group:artifact' (e.g. 'org.jetbrains.kotlinx:kotlinx-serialization-json')."
    },
    "versions": {
      "type": "boolean",
      "description": "Setting to true retrieves all available versions for a 'group:artifact'. Ideal for researching release history."
    },
    "offset": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Specifying an offset for large results to enable efficient pagination."
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Limiting the number of results to maintain token efficiency and reduce noise. Default is 10."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




