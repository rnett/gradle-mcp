[//]: # (@formatter:off)

# Dependency Search Tools

Tools for querying maven repositories for dependency information.

## lookup_maven_versions

Retrieves all released versions for a Maven `group:artifact` from deps.dev, sorted most-recent first with `yyyy-MM-dd` publish dates.
Covers the full Maven package index including packages published via the new Central Portal (central.sonatype.com).
Use to verify exact release history instead of hallucinated version numbers; then use `inspect_dependencies` to check if the project already uses the library.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "coordinates": {
      "type": "string",
      "description": "Maven coordinates in 'group:artifact' format, e.g. 'org.jetbrains.kotlinx:kotlinx-coroutines-core'."
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
      "description": "offset = zero-based start index (default 0); limit = max versions to return (default 5)."
    }
  },
  "required": [
    "coordinates"
  ],
  "type": "object"
}
```


</details>




