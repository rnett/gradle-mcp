[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## gradle_docs

Search and read official Gradle documentation, including the User Guide, DSL Reference, Javadoc, and Release Notes.

### Features
- **Unified Search**: Search across all documentation or scope to specific sections using tags.
- **Scoped Searching**: Use the `tag:` syntax in your query to target specific documentation areas:
  - `tag:userguide <query>`: Search the Gradle User Guide.
  - `tag:dsl <query>`: Search the DSL Reference (Groovy and Kotlin DSL).
  - `tag:javadoc <query>`: Search the Java API Reference.
  - `tag:samples <query>`: Search Gradle samples and examples.
  - `tag:release-notes <query>`: Search within version release notes.
- **Direct Page and Asset Access**: Read specific pages (.md) or view images (.png, .jpg, etc.) by providing their `path`.
- **Section Summaries**: Call with no arguments to see available documentation sections and their content counts for the targeted version.

### Common Usage Patterns
- **Summary of Docs**: `gradle_docs()`
- **Search User Guide**: `gradle_docs(query="tag:userguide working with files")`
- **Read Page**: `gradle_docs(path="userguide/command_line_interface.md")`
- **Read Image**: `gradle_docs(path="userguide/img/command-line-options.png")`
- **Target Specific Version**: `gradle_docs(query="tag:release-notes", version="8.5")`

Note: `path` takes precedence over `query`.
For detailed navigation strategies and available tags, refer to the `gradle-docs` skill.

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
      "description": "The search query for the documentation. Use `tag:<section>` to filter (e.g., `tag:userguide configuration`). Available tags: `userguide`, `dsl`, `javadoc`, `samples`, `release-notes`."
    },
    "path": {
      "type": [
        "string",
        "null"
      ],
      "description": "The specific documentation page or image path to read (e.g., 'userguide/command_line_interface.md' or 'userguide/img/cli.png'). If omitted and no query is provided, a summary of documentation sections is returned."
    },
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "The specific Gradle version documentation to target (e.g., '8.6'). Defaults to the project's detected version or the latest release."
    },
    "projectRoot": {
      "type": [
        "string",
        "null"
      ],
      "description": "The absolute path to the project root directory. Used to automatically detect the project's Gradle version for documentation targeting."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>




