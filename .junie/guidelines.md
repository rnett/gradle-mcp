# Project Guidelines

This is a Gradle project a MCP server written using the Kotlin SDK that adds tools for interacting with and querying Gradle.

Use the Gradle MCP to interact with Gradle whenever possible.

## Project structure notes

* The `test` task should be used to run tests.

## Code style notes

* Always name tests using descriptive names in english, using backticks.
* Always put dependencies in the version catalog.
* Use only the kotlin.test assertions configured with power-assert. Power-assert makes it unnecessary to use more complex assertions. Generally prefer to just use `kotlin.assert`.
* Avoid mocking wherever possible.
