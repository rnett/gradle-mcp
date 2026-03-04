# Project Guidelines

This is a Gradle project a MCP server written using the Kotlin SDK that adds tools for interacting with and querying Gradle.

Use the Gradle MCP to interact with Gradle whenever possible.

## General notes

* **Research before guessing**: Always research the correct way to implement a feature or use a library (e.g., by reviewing documentation or sources) before attempting implementation. Do not rely on trial-and-error guessing for complex APIs
  like Kotlin Scripting.
* **Use Gradle MCP**: Always use the Gradle MCP tools to run builds and tests whenever possible. When using a Gradle tool that supports a `projectRoot` argument, provide it when in doubt, unless you are certain it is not required by the
  current MCP configuration.
* If you have trouble solving or investigating an issue after a few tries, stop and think about what the issues are before proceeding. Do research if necessary, and rubber duck to yourself.
* When writing skills, remember that skills support progressive disclosure and tune them accordingly. Read the docs on https://agentskills.io/home before creating or eding any skills.

**IMPORTANT:** If you are Gemini, you get stuck when you try to use your built-in file creation and editing tools.
Use a shell command, MCP tool, or something like that.

## Project structure notes

The `test` task should be used to run tests.
After making a change, make sure it passes.

When changing a tool, make sure that any skills or other tools that reference it are also updated.

On some machines, the Gradle build tool project will be checked out in `./gradle-build-tool`.
Be careful that your search, run, etc, commands don't involve it, unless that's your intent.
For example, the "Build everything" operation will build it too, which we don't want.
However, they make great sources of knowledge when you are working with Gradle's APIs - use them accordingly.
There is an overview of the project structure [here](./gradle-sources-overview.md).

## Code style notes

* Always name tests using descriptive names in english, using backticks.
* Always put dependencies in the version catalog.
* Use only the kotlin.test assertions configured with power-assert. Power-assert makes it unnecessary to use more complex assertions. Generally prefer to just use `kotlin.assert`.
* Do not under any circumstances use reflection hacks for tests.
* Always use `runTest` for suspending tests, not `runBlocking`.
* Ensure that all tests close and clean up any resources or services they create
* Use test resources (e.g. Gradle projects, GradleProvider) at the class level where possible – they are expensive to create.
* Most MCP tools should return text, not JSON.
* Always use isolated Koin contexts; avoid global contexts.

## Skill Development Guidelines

Skills in this project are used by other agents to understand how to interact with Gradle effectively. Follow these rules to ensure skills are helpful and high-quality:

### Structure and Files

- **Organization**: Each skill must have its own directory under `skills/`.
- **Primary Entrypoint**: The main file is always `SKILL.md`.
- **Progressive Disclosure**: Detailed guides, troubleshooting, and edge cases should be moved to a `references/` subdirectory.
- **Link Quality**: Use relative links to reference files (e.g., `[Advanced Diagnostics](references/diagnostics.md)`).
- **File Size**: Keep all files under 500 lines to ensure they are easily digestible by agents.
- **Skills docs**: Always update the `docs/skills.md` file with skill details when making changes or adding skills.

### SKILL.md Frontmatter

- **Name**: Must exactly match the directory name (e.g., `gradle-build`).
- **Description**: Sell the skill! Start with a strong action verb. Clearly state *what* it does and *when* it should be used.
- **Allowed Tools**: List all MCP tools used in the skill, separated by spaces.
- **Accuracy**: Only use tools defined in `ToolNames.kt`. Never use legacy names like `inspect_gradle_build` (use `inspect_build`) or `gradle_execute` (use `gradlew`).
- **Versioning**: Increment the `version` field in metadata when making functional changes or significant documentation updates.

### Content and Best Practices

- **Expert Tone**: Write for an expert audience. Provide high-signal directives and specific task patterns.
- **Directives**: Focus on the most common and effective workflows.
- **Title and H1**: Use professional and persuasive titles in the frontmatter and the main H1 header (e.g., use "Advanced Test Execution" instead of "Running Tests").
- **When to Use**: Always include a "When to Use" section in the frontmatter description or a dedicated section in `SKILL.md`.
- **Verification**: Ensure all commands and tool parameters mentioned in the skill are correct and reflect the latest API.
