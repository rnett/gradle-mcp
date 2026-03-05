# Project Guidelines

This is a Gradle project a MCP server written using the Kotlin SDK that adds tools for interacting with and querying Gradle.

Use the Gradle MCP to interact with Gradle whenever possible.

## General notes

* **Research before guessing**: Always research the correct way to implement a feature or use a library (e.g., by reviewing documentation or sources) before attempting implementation. Do not rely on trial-and-error guessing for complex APIs
  like Kotlin Scripting.
* **Use Gradle MCP**: Always use the Gradle MCP tools to run builds and tests whenever possible. When using a Gradle tool that supports a `projectRoot` argument, provide it when in doubt, unless you are certain it is not required by the
  current MCP configuration.
* If you have trouble solving or investigating an issue after a few tries, stop and think about what the issues are before proceeding. Do research if necessary, and rubber duck to yourself.
* When writing skills, remember that skills support progressive disclosure and tune them accordingly. Read the docs on https://agentskills.io/home before creating or editing any skills.
* Add your changes to git that you want to persist (i.e. not temp files), but don't EVER create commits or push.
* When testing your changes, run related tests and make sure they pass before moving on to `check`.
* If you changed the tool descriptions or metadata, you will need to run `:updateToolsList` before `check` will pass.

## Changes and specs

When instructed to create a proposal, look at [the roadmap](./roadmap.md) first, it's usually coming from there.

When making significant changes to the code, e.g. using an openspec process, here are some guidelines:

* **Always run the `check` task** before declaring a change or spec completed. This ensures that all tests, linting, and generated documentation are correct and up-to-date.
* **Ensure that your changes have automated tests**. We don't need or go for 100% coverage, but ensure that there's a decent amount, especially of any delicated or complex parts. Ensure that the important behaviors from the spec and
  proposal are tested.
* Include a test plan in your planning documents, probably as a spec. See the above item.
* Ensure that the tests are meaningful, with meaningful assertions that will actually catch bugs or mistakes. Take a step back when writing the tests and focus more on the original goals than the implementation - the goal is to ensure that
  the implementation meets the original goals, and does not have bugs.
* Test classes should always be named after the service, not the implementation (e.g. no Default*Test).
* Keep test performance in mind. Our tests already take quite some time to run, and we don't want to make things worse. Keep this in mind when deciding how many tests to add, and when writing the tests.

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
- **Accuracy**: Only use tools defined in `ToolNames.kt`. Never use legacy names like `inspect_gradle_build` (use `inspect_build`) or `gradle_execute` (use `gradle`).
- **Versioning**: Increment the `version` field in metadata when making functional changes or significant documentation updates.

### Content and Best Practices

- **Expert Tone**: Write for an expert audience. Provide high-signal directives and specific task patterns.
- **Directives**: Focus on the most common and effective workflows.
- **Title and H1**: Use professional and persuasive titles in the frontmatter and the main H1 header (e.g., use "Advanced Test Execution" instead of "Running Tests").
- **When to Use**: Always include a "When to Use" section in the frontmatter description or a dedicated section in `SKILL.md`.
- **Verification**: Ensure all commands and tool parameters mentioned in the skill are correct and reflect the latest API.
