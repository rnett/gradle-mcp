# Project Guidelines

This is a Gradle project a MCP server written using the Kotlin SDK that adds tools for interacting with and querying Gradle.

Use the Gradle MCP to interact with Gradle whenever possible.

## General notes

* **Research before guessing**: Always research the correct way to implement a feature or use a library (e.g., by reviewing documentation or sources) before attempting implementation. Do not rely on trial-and-error guessing for complex APIs
  like Kotlin Scripting.
* **Use Gradle MCP**: Always use the Gradle MCP tools to run builds and tests whenever possible.
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
