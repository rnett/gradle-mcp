[![Maven Central Version](https://img.shields.io/maven-central/v/dev.rnett.gradle-mcp/gradle-mcp?style=for-the-badge)](https://central.sonatype.com/artifact/dev.rnett.gradle-mcp/gradle-mcp)
![Maven snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Frnett%2Fgradle-mcp%2Fgradle-mcp%2Fmaven-metadata.xml&strategy=latestProperty&style=for-the-badge&label=SNAPSHOT&color=yellow)
[![GitHub Repo](https://img.shields.io/badge/github-181717?style=for-the-badge&logo=github)](https://github.com/rnett/gradle-mcp)
[![GitHub License](https://img.shields.io/github/license/rnett/gradle-mcp?style=for-the-badge)](./LICENSE)

# Gradle MCP server

A Model Context Protocol (MCP) server for Gradle. It gives AI agents the tools they need to explore project structures, run tasks, audit dependencies, and interact with the JVM runtime.

##### Features

**Source downloading and indexing tools will be moving to a dedicated MCP/cli soon.**

* **Agent Skills**: Built-in workflows that guide AI agents through complex Gradle tasks. See the [documentation](./skills.md) for more.
* **Project Mapping**: Easily explore multi-project structures, modules, tasks, and properties.
* **Smart Task Execution**: Run builds in the background, monitor progress, and capture specific task outputs without the noise. Supports advanced environment control and shell environment sourcing.
* **Advanced Testing**: Run filtered test suites and get full access to logs and stack traces for every test case.
* **Dependency & Source Search**: Search Maven Central and browse the source code of your dependencies or Gradle's internal engine.
* **Interactive Kotlin REPL**: Test project utilities and explore APIs in a persistent REPL with access to all your classes.
* **Compose UI Previews**: Render UI components directly to images from the project runtime for visual auditing.
* **Gradle Documentation**: Instant access to searchable, indexed Gradle User Guides and DSL references.
* **Develocity Build Scans**: One-click publishing of Build Scans for deep troubleshooting.
* **Token Optimized**: Compact data formats designed to keep context usage low.

## Installation

[//]: # (@formatter:off)
!!! warning "JDK Requirement"
    JDK 21 or higher is required to run `gradle-mcp`.
    You can use JBang to install JDKs too: [docs](https://www.jbang.dev/documentation/jbang/latest/javaversions.html).

[//]: # (@formatter:on)

Use [jbang](https://www.jbang.dev/documentation/jbang/latest/installation.html):

```shell
# For releases
jbang run --quiet --fresh gradle-mcp@rnett

# For snapshots
jbang run --quiet --fresh gradle-mcp-snapshot@rnett
```

Alternatively, run the GAV directly:

```shell
# For releases
jbang run --fresh dev.rnett.gradle-mcp:gradle-mcp:+

# For snapshots
jbang run --fresh \
  --repos snapshots=https://central.sonatype.com/repository/maven-snapshots/ \
  dev.rnett.gradle-mcp:gradle-mcp:+
```

You can add an alias to make invoking it easier:

```shell
jbang alias add dev.rnett.gradle-mcp:gradle-mcp:+
```

Then run it with `jbang --fresh gradle-mcp`.

Or even install it as a command (`gradle-mcp`):

```shell
jbang app setup
jbang app install --name gradle-mcp dev.rnett.gradle-mcp:gradle-mcp:+
```

See [jbang documentation](https://www.jbang.dev/documentation/jbang/latest/install.html) for more details.

#### Example MCP configuration

```json
{
  "mcpServers": {
    "gradle": {
      "command": "jbang",
      "args": [
        "run",
        "--quiet",
        "--fresh",
        "gradle-mcp@rnett"
      ]
    }
  }
}
```

```json
{
  "mcpServers": {
    "gradle": {
      "command": "bash -c",
      "args": [
        "jbang run --quiet --fresh gradle-mcp@rnett"
      ]
    }
  }
}
```

## Configuration

The `GRADLE_MCP_PROJECT_ROOT` environment variable can be set to provide a default Gradle project root.
This is used if no project root is specified in a tool call and there isn't exactly one MCP root configured.

## Usage

Run the server.
By default it runs in STDIO mode.
You can use the `server` argument to run it as a server on port 47813.

## Agent Skills

Agent Skills are specialized guides that help AI agents navigate common Gradle workflows reliably.

Included skills:

- `running_gradle_builds`: Running Gradle Commands, Background Jobs, and Investigating Failures.
- `running_gradle_tests`: Running and Investigating Tests.
- `managing_gradle_dependencies`: Auditing and updating dependencies.
- `introspecting_gradle_projects`: Mapping project structure, modules, and tasks.
- `searching_dependency_sources`: Searching and reading dependency source code.
- `interacting_with_project_runtime`: Running Code in the Project's Environment (REPL).
- `researching_gradle_internals`: Searching and reading the Gradle User Guide and source code.
- `verifying_compose_ui`: Visually verifying Compose UI components.
- `gradle_expert`: Senior Build Engineer guidance for build scripts and failures.

For instructions on how to use these skills, see the [Agent Skills](skills.md) documentation.

[//]: # (@formatter:off)
!!! danger
    **DO NOT EVER EXPOSE THIS SERVER TO THE INTERNET.**
[//]: # (@formatter:on)

## Publishing Build Scans

Even if you don't have your build configured to publish build scans automatically, you can still publish build scans - just ask your agent to publish a scan when invoking Gradle.
These will publish to the public https://scans.gradle.com instance unless you have a Develocity instance configured in your build.
