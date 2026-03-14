## ADDED Requirements

### Requirement: Inspect Build Tool Progress Reporting

The `inspect_build` tool SHALL report progress to the client when waiting for a background build to complete or reach a specific state.

#### Scenario: Waiting for build completion

- **WHEN** `inspect_build` is called with a `wait` parameter for an active build
- **THEN** it periodically reports progress (e.g. percentages, task states) via the MCP progress notification mechanism until the wait condition is met or the build finishes.

#### Scenario: Wait condition met before build completes

- **WHEN** `inspect_build` is waiting on a specific task (`waitForTask`) or log line (`waitFor`)
- **THEN** it reports progress during the wait period and ceases reporting once the condition is met and the tool returns, even if the build continues in the background.
