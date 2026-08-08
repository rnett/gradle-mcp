# Capability: using-gradle

## Purpose

Route AI agents through structured build-result, environment, and project-graph diagnostics before they resort to low-signal file inspection.

## ADDED Requirements

### Requirement: The skill routes failed builds to structured problems first

The `using-gradle` skill MUST instruct agents diagnosing FAILED builds or low-signal errors to query `query_build kind=PROBLEMS` before starting file-read investigation. It SHALL explain that PROBLEMS results include identifiers, severity, documentation, occurrence details, and potential solutions.

#### Scenario: Agent triages a failed build

- **WHEN** an agent receives a FAILED build result
- **AND** the initial error is absent or not actionable
- **THEN** the skill routes the agent to `query_build kind=PROBLEMS`
- **AND** file inspection follows only when structured problems do not resolve the diagnosis

### Requirement: The skill explains completed build-result intelligence

The `using-gradle` skill SHALL teach agents to read task outcome reasons and provenance, frozen phase counts, task-origin aggregation, and the nullable configuration-cache report pointer from build-query output.

#### Scenario: Agent explains reused or skipped work

- **WHEN** an agent needs to explain why a task did not execute normally
- **THEN** the skill routes it to TASKS output
- **AND** instructs it to interpret outcome, reason, and provenance together

#### Scenario: Agent locates build costs and origins

- **WHEN** an agent needs to explain where completed build work occurred
- **THEN** the skill instructs it to inspect phase counts and task-origin aggregation
- **AND** treats phase counts as a frozen completed-build snapshot

#### Scenario: Agent investigates configuration-cache problems

- **WHEN** build output contains a configuration-cache report pointer
- **THEN** the skill instructs the agent to use the pointer as a verbatim report location
- **AND** routes structured problem diagnosis through `query_build kind=PROBLEMS`
- **AND** does not instruct the MCP server to parse the report

### Requirement: The skill routes build-environment questions through existing Gradle diagnostics

The `using-gradle` skill SHALL route JDK and daemon questions through `javaToolchains`, `buildEnvironment`, and `--version`, distinguishing IDE, CLI, daemon, and toolchain state without introducing a standalone tool.

#### Scenario: Agent identifies the active JDK or daemon environment

- **WHEN** an agent asks which JDK, daemon, or toolchain a build uses
- **THEN** the skill routes it to `javaToolchains`, `buildEnvironment`, and `--version` as appropriate
- **AND** explains that IDE, CLI, daemon, and toolchain selections may differ

### Requirement: The skill routes project ownership through the Gradle project graph

The `using-gradle` skill SHALL route multi-project, composite-build, convention-plugin, and task-ownership questions through `projects`, then `tasks --all`, then `help --task`, without introducing a standalone tool.

#### Scenario: Agent traces task ownership in a complex build

- **WHEN** an agent needs to identify project or plugin ownership for a task
- **THEN** the skill first routes it to `projects`
- **AND** then to `tasks --all` for the relevant project scope
- **AND** then to `help --task` for authoritative task details
