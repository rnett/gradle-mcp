[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## gradle_docs

The authoritative tool for searching and reading official Gradle documentation, release notes, and version-specific guides.
It provides high-performance access to the entire Gradle User Guide, rendered as markdown for seamless agent consumption.

### Authoritative Features
- **Precision Search**: Use `query` to find specific sections, DSL elements, or plugin documentation across the entire authoritative guide.
- **Exhaustive Content Retrieval**: Read full documentation pages directly in your context using the `path` argument.
- **Authoritative Release Insights**: Set `releaseNotes=true` to retrieve the definitive list of changes, improvements, and deprecations for any Gradle version.
- **Version-Specific Targeting**: Automatically targets your project's Gradle version or allows for surgical manual version selection.

### Common Usage Patterns
- **Search User Guide**: `gradle_docs(query="kotlin dsl configuration")`
- **Read Guide Section**: `gradle_docs(path="working_with_files.html")`
- **Check Version Changes**: `gradle_docs(releaseNotes=true, version="8.5")`
- **List Available Pages**: `gradle_docs()`

Note: `releaseNotes` takes precedence over `path`, which takes precedence over `query`.
For detailed documentation navigation strategies, refer to the `gradle-docs` skill.

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
      "description": "The authoritative search query for the documentation. Supports keywords and phrases to find relevant User Guide sections."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "The specific documentation page path to read (e.g., 'command_line_interface.html'). If omitted, a searchable list of all pages is returned."
    },
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "The specific Gradle version documentation to target (e.g., '8.6'). Defaults to the project's detected version or the latest release."
    },
    "releaseNotes": {
      "type": "boolean",
      "description": "If true, fetches the authoritative release notes for the specified version. Ideal for researching breaking changes and new features."
    },
    "projectRoot": {
      "type": [
        "string",
        "null"
      ],
      "description": "The absolute path to the project root directory. Used to automatically detect the project's Gradle version for high-resolution documentation targeting."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




