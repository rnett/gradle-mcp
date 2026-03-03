[//]: # (@formatter:off)

# Skill Tools

Tools for managing Gradle MCP skills.

## install_gradle_skills

Installs a set of skills for working with Gradle into the specified directory.

These skills provide comprehensive guidance and best practices for various Gradle-related tasks, including:
- Running and troubleshooting builds
- Build authoring and optimization
- Dependency management and troubleshooting
- Project introspection and structure analysis
- Effective use of Gradle documentation
- Running and debugging tests
- Using the Gradle REPL for interactive development, debugging, and testing

Installing these skills allows your agent to access structured knowledge and specialized instructions to perform Gradle tasks more effectively and follow best practices.
You should pass the directory where you want the skills to be installed, typically your own skills or documentation directory.

Use the `force` option to replace existing skills with the ones from this tool.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "directory": {
      "type": "string",
      "description": "The directory to install the skills to. This should be a directory where your calling agent can find and use skills (e.g. its skills directory)."
    },
    "force": {
      "type": "boolean",
      "description": "If true, replaces existing skills in the target directory. If false (default), skips skills that already exist."
    }
  },
  "required": [
    "directory"
  ],
  "type": "object"
}
```


</details>




