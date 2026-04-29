# Capability: testing-standards

## Purpose

Defines the project's standards for automated testing, including idioms for Power Assert, MockK, and verification of MCP tools.

## Requirements

### Requirement: Power Assert Idioms

The system SHALL use Power Assert for rich failure messages in all Kotlin tests.

- **Complexity**: Developers SHOULD avoid overly nested assertions to prevent compiler crashes.
- **Nullable Properties**: Tests SHALL avoid using the `!!` operator on results before assertion. Use direct assertions on nullable properties (e.g., `assertTrue(result.property.contains(...))`) to allow Power Assert to display the actual
  values in failure reports.

### Requirement: Service Design for Testability

Service interfaces SHALL be designed to be easily mocked and future-proofed.

- **Data Classes for Parameters**: When refactoring service interfaces, developers SHALL use **data classes for parameters** (e.g., `DependencyRequestOptions`). This avoids "boolean blindness" and allows adding new configuration flags with
  defaults without breaking existing test call sites.

### Requirement: MCP Tool Verification

Integration tests for MCP tools SHALL verify the final re-rendered output rather than raw task output.

- **Output Formatting**: Assertions MUST check the re-rendered format (e.g., `Project: :path`) as seen by the MCP client.
- **Serialization**: When calling MCP tools from tests using `server.client.callTool`, developers MUST use `kotlinx.serialization.json.buildJsonObject` for arguments containing complex types like `List` or `Map`.
- **Large Result Inspection**: Tests that expect large outputs (e.g., console logs) SHOULD use the `outputFile` option in `inspect_build` to write results to a file for more efficient token usage and to bypass pagination.
- **Test Discovery**: Tests that search for other tests MUST use simple name prefixes without parentheses when searching with `inspect_build`.

### Requirement: Testing Asynchronous Events

Tests SHALL use deterministic synchronization for asynchronous operations.

- **Signals**: Developers MUST use `CompletableDeferred<Unit>` as a "signal" that can be completed by a tracker and awaited by the test.
- **Avoid Delays**: Tests SHALL NOT use `delay()` or `Dispatchers.Unconfined` for synchronization.
- **Log Processing**: When testing asynchronous log loops, developers SHALL use a `CompletableDeferred` to signal completion of the processing loop.

### Requirement: Test Lifecycle & Environment

Tests SHALL be designed to run reliably in a containerized or resource-constrained environment.

- **Background Scopes**: Developers MUST use `backgroundScope` in `runTest` when creating objects that launch long-lived background coroutines (like `RunningBuild`) to ensure clean termination.
- **REPL Environment**: Integration test classes inheriting from `BaseReplIntegrationTest` MUST ensure `createProvider()` is overridden to return a `DefaultGradleProvider` (not a relaxed mock), as the REPL environment resolution relies on
  real Gradle builds.
- **Worker Crashes**: Managers of external worker processes (like the REPL worker) MUST implement a small circular buffer for `stderr` lines in their session state to provide immediate feedback when a process terminates unexpectedly.
- **Resource Management**: All resources (e.g., `HttpClient` instances, REPL workers) MUST be explicitly closed using `@AfterEach` or `AutoCloseable` to prevent leaks.
