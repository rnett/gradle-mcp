[//]: # (@formatter:off)

# Dependency Search Tools

Tools for querying maven repositories for dependency information.

## search_maven_central

Searches Maven Central for library coordinates and version histories; use instead of hallucinated versions or generic web searches.

- **Coordinate Discovery**: Search by name, group, or artifact ID snippet.
- **Version Research**: Set `versions=true` with `group:artifact` query to list all released versions.
- Once identified, use `inspect_dependencies` to check if the project already uses the library.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": "string",
      "description": "Artifact name, group, or coordinates. If versions=true, MUST be exactly 'group:artifact'."
    },
    "versions": {
      "type": "boolean",
      "description": "Retrieve all versions for a 'group:artifact'. Ideal for researching release history."
    },
    "offset": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Offset for pagination over large result sets."
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "Max results to return. Default is 10."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




