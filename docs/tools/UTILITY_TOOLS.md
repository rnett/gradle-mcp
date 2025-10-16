[//]: # (@formatter:off)

# Utility Tools

Utility tools that don't run Gradle directly.

## get_gradle_project_containing_file

Gets the nearest Gradle project containing the given file if there is one.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "path": {
      "type": "string",
      "description": "The target file's path. Must be absolute."
    }
  },
  "required": [
    "path"
  ],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "projectRootPath": {
      "type": "string",
      "description": "The file system path of the Gradle project's root"
    },
    "projectPath": {
      "type": "string",
      "description": "Gradle project path of the project containing the file, e.g. ':project-a'"
    }
  },
  "required": [
    "projectRootPath",
    "projectPath"
  ],
  "type": "object"
}
```


</details>

## get_gradle_docs_link

Get a link to the Gradle documentation for the passed version or the latest if no version is passed

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "version": {
      "type": "string",
      "description": "The Gradle version to get documentation for. Uses the latest by default. Should be a semver-like version with 2 or 3 numbers."
    }
  },
  "required": [],
  "type": "object"
}
```


</details>


## get_current_environment_variables

Gets the current environment variables for the MCP server, as a map of variable name to value. These are the environment variables that will be used when executing Gradle tasks, plus any additional env vars from the invocation args.

<details>

<summary>Input schema</summary>


```json
{
  "required": [],
  "type": "object"
}
```


</details>


<details>

<summary>Output schema</summary>


```json
{
  "properties": {
    "environment": {
      "type": "object",
      "additionalProperties": {
        "type": "string"
      }
    }
  },
  "required": [
    "environment"
  ],
  "type": "object"
}
```


</details>



