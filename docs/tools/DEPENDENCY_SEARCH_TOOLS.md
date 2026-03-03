[//]: # (@formatter:off)

# Dependency Search Tools

Tools for querying maven repositories for dependency information.

## search_maven_central

Find libraries or view version history on Maven Central.

Use this tool to discover new dependencies or to find available versions of a library.
Once you have found a dependency, you can check if it is already used in your project using the `inspect_dependencies` tool.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": "string",
      "description": "The search query or GAV identifier."
    },
    "versions": {
      "type": "boolean",
      "description": "If true, list all available versions for the artifact."
    },
    "offset": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The offset to start from in the results."
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of results to return."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




