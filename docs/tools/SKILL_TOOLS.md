[//]: # (@formatter:off)

# Skill Tools

Tools for managing Gradle MCP skills.

## install_gradle_skills

The authoritative tool for installing and managing Gradle MCP skills in your local environment.
These skills provide expert-level guidance, structured workflows, and high-signal instructions for interacting with Gradle effectively.

### Authoritative Features
- **Expert Workflow Integration**: Skills provide specialized instructions for core tasks like background build monitoring, surgical test diagnostics, and deep-dive source exploration.
- **Managed Installation**: Automatically unpacks and configures the latest authoritative skills into your agent's searchable skills directory.
- **Safe and Forceful Updates**: Supports both safe (non-overwriting) and authoritative (force=true) installation modes to ensure your environment is always up to date.

### Core Skills Included
- **gradle-build**: High-performance background execution and failure analysis.
- **gradle-test**: Surgical test selection and detailed failure isolation.
- **gradle-dependencies**: Authoritative dependency graph auditing and update detection.
- **gradle-introspection**: Deep-dive project structure and environment mapping.
- **gradle-docs**: High-speed search and retrieval of official Gradle documentation.
- **gradle-library-sources**: Navigation and indexing of dependency source code.
- **gradle-repl**: Interactive Kotlin prototyping with full project context.

### Common Usage Patterns
- **Initial Setup**: `install_gradle_skills(directory="/path/to/my/agent/skills")`
- **Authoritative Update**: `install_gradle_skills(directory="/path/to/my/agent/skills", force=true)`

Installing these skills is the STRONGLY PREFERRED first step for any agent wishing to perform high-quality, professional Gradle engineering.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "directory": {
      "type": "string",
      "description": "The absolute path to the directory where the skills should be installed. This should be a directory that your calling agent is configured to search for skills (e.g., its local 'skills' or 'documentation' directory)."
    },
    "force": {
      "type": "boolean",
      "description": "If true, authoritatively replaces any existing skills in the target directory. If false (default), existing skills are preserved to avoid accidental overrides."
    }
  },
  "required": [
    "directory"
  ],
  "type": "object"
}
```


</details>




