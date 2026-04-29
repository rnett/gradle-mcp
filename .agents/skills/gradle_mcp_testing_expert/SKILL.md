---
name: gradle_mcp_testing_expert
description: >-
  Workflows for project-specific testing, including failure diagnosis and tool verification.
metadata:
  author: rnett
  version: "1.1"
---

# Skill: Gradle MCP Testing Expert

This skill provides expert procedural guidance for writing and running tests within the Gradle MCP project.

## Workflow: Authoring Tests
1. **Tech Stack**: Use JUnit 5 with Power Assert and MockK.
2. **Standards**: ALWAYS refer to the [testing-standards](openspec/specs/testing-standards/spec.md) spec for idioms (Power Assert, mocking data classes).
3. **Integration**: If inheriting from `BaseReplIntegrationTest`, ensure `createProvider()` returns a `DefaultGradleProvider`.

## Workflow: Verifying MCP Tools
1. **Tool Call**: Use `server.client.callTool` with `buildJsonObject` for complex arguments.
2. **Assertion**: Verify the final re-rendered format (e.g., `Project: :path`).
3. **Large Outputs**: Use the `outputFile` option in `inspect_build` to capture large results for efficient inspection.

## Workflow: Diagnosing Failures
1. **Worker Crashes**: Check the session's stderr circular buffer for immediate feedback on process crashes.
2. **Timeouts**: If tests fail in CI or on slow machines, verify they have a generous timeout (default should be 10m).
3. **Resource Leaks**: Check for unclosed `HttpClient` or REPL workers if tests hang.
