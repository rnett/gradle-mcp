## MODIFIED Requirements

### Requirement: Test Lifecycle & Environment

Tests SHALL be designed to run reliably in a containerized or resource-constrained environment.

- **Background Scopes**: Developers MUST use `backgroundScope` in `runTest` when creating objects that launch long-lived background coroutines (like `RunningBuild`) to ensure clean termination.
- **REPL Environment**: Integration test classes inheriting from `BaseReplIntegrationTest` MUST ensure `createProvider()` is overridden to return a `DefaultGradleProvider` (not a relaxed mock), as the REPL environment resolution relies on real Gradle builds.
- **Worker Crashes**: Managers of external worker processes (like the REPL worker) MUST implement a small circular buffer for `stderr` lines in their session state to provide immediate feedback when a process terminates unexpectedly.
- **Resource Management**: All resources (e.g., `HttpClient` instances, REPL workers, real `GradleProvider` instances) MUST be explicitly closed using `@AfterEach`, `AutoCloseable`, or `use`/`finally` to prevent leaks.
- **GradleProvider Lifecycle**: Tests that create a real `DefaultGradleProvider` MUST close it deterministically after use; `close()` cancels provider-owned builds and coroutine scopes but does NOT stop Gradle daemons.
- **Real Nested Builds**: Real nested Gradle builds SHALL route through the standardized test defaults (`withTestGradleDefaults`) so daemon identity and JVM arguments are canonical across the suite; explicit arguments supplied through the defaults take precedence over the canonical fill-ins.

#### Scenario: Real provider closed after a test run

- **WHEN** a test creates a real `DefaultGradleProvider`
- **THEN** the test SHALL close it deterministically after use
- **AND** the test SHALL NOT expect `close()` to stop Gradle daemons

#### Scenario: Nested build uses standardized defaults

- **WHEN** a test runs a real nested Gradle build
- **THEN** the build SHALL use the standardized `withTestGradleDefaults` identity and JVM arguments
