[//]: # (@formatter:off)

# Dependency Search Tools

Tools for querying maven repositories for dependency information.

## search_maven_central

The authoritative tool for discovering libraries and exploring version histories on Maven Central.
It provides surgical access to the world's largest repository of Java and Kotlin artifacts directly from your agentic workflow.

### High-Performance Features
- **Precision Artifact Discovery**: Search for libraries by name, group, or description. Identify the exact Group:Artifact:Version (GAV) coordinates needed for your build file.
- **Exhaustive Version Auditing**: Set `versions=true` to retrieve the complete release history for any artifact. Ideal for identifying stable update paths or researching legacy versions.
- **Managed Pagination**: Efficiently browse large result sets using `offset` and `limit` to maintain optimal context usage.

### Common Usage Patterns
- **General Search**: `search_maven_central(query="serialization")`
- **Find Latest GAV**: `search_maven_central(query="org.jetbrains.kotlinx:kotlinx-serialization-json")`
- **List All Versions**: `search_maven_central(query="org.junit.jupiter:junit-jupiter", versions=true)`

Once you have identified a dependency, use the `inspect_dependencies` tool to check if it is already used in your project and audit its integration status.
For detailed discovery strategies, refer to the `gradle-dependencies` skill.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": "string",
      "description": "The search query (e.g., 'kotlinx-serialization') or authoritative GAV identifier (e.g., 'org.jetbrains.kotlinx:kotlinx-serialization-json')."
    },
    "versions": {
      "type": "boolean",
      "description": "If true, lists all available versions for the specified 'group:artifact'. This is the authoritative way to explore an artifact's release history."
    },
    "offset": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The offset to start from in the results. Use this with 'limit' for efficient pagination through large search results."
    },
    "limit": {
      "type": [
        "integer",
        "null"
      ],
      "minimum": -2147483648,
      "maximum": 2147483647,
      "description": "The maximum number of results to return. Use a smaller limit to maintain token efficiency and reduce noise in your context."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




