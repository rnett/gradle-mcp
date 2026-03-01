[//]: # (@formatter:off)

# Gradle Docs Tools

Tools for querying and reading Gradle documentation.

## get_all_gradle_docs_pages

Returns a list of all Gradle User Guide documentation pages for a given version. The list includes the page title and its path. The path is relative to the base URL: `https://docs.gradle.org/{version}/userguide/`. For example, a path of `command_line_interface.html` corresponds to `https://docs.gradle.org/current/userguide/command_line_interface.html`. The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle version to get documentation for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root."
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


## get_gradle_docs_page

Returns the content of a specific Gradle User Guide documentation page as Markdown. The path should be relative to the base URL: `https://docs.gradle.org/{version}/userguide/`. For example, to get the page for `https://docs.gradle.org/current/userguide/command_line_interface.html`, the path should be `command_line_interface.html`. The path must not contain `..`. The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "path": {
      "type": "string",
      "description": "The relative path to the documentation page (e.g. 'gradle_basics.html') from https://docs.gradle.org/{version}/userguide/."
    },
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle version to get documentation for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root."
    },
    "projectRoot": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified."
    }
  },
  "required": [
    "path"
  ],
  "type": "object"
}
```


</details>


## get_gradle_release_notes

Returns the release notes for a specific Gradle version as Markdown. The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle version to get release notes for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root."
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


## search_gradle_docs

Searches Gradle User Guide documentation for a given version. Returns the matching page titles, paths, and snippets. The returned path is relative to the base URL: `https://docs.gradle.org/{version}/userguide/`. The version will be autodetected from the Gradle project root if not specified. If autodetection fails and no version is provided, it defaults to 'current'.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "query": {
      "type": "string",
      "description": "The search query."
    },
    "isRegex": {
      "type": "boolean",
      "description": "Whether the query is a regex. Defaults to false."
    },
    "version": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle version to search documentation for (e.g. '8.5', '7.6.3'). Defaults to 'current' if version is not provided and cannot be autodetected from the project root."
    },
    "projectRoot": {
      "type": [
        "string",
        "null"
      ],
      "description": "The Gradle project root to detect the version from. If not provided, it will be autodetected from the current working directory or MCP roots if possible. Ignored and not needed if the version is specified."
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```


</details>




