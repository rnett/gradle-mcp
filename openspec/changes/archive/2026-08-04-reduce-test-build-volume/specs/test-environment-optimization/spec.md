## ADDED Requirements

### Requirement: Real-Gradle suites bind fork concurrency

Real-Gradle test suites (e.g., `integrationTest`, `treeSitterTest`) SHALL bind test-task fork concurrency to the execution environment: local runs keep 8 forks and CI keeps 3 forks, and any local reduction to 4 forks SHALL only happen after a baseline probe records the suite wall time and peak daemon count that justify it.

#### Scenario: Local fork concurrency stays at 8 initially

- **WHEN** `integrationTest` or `treeSitterTest` runs locally
- **THEN** `maxParallelForks` SHALL remain 8

#### Scenario: CI fork concurrency stays at 3

- **WHEN** `integrationTest` or `treeSitterTest` runs in CI (`CI` environment variable set)
- **THEN** `maxParallelForks` SHALL be 3

#### Scenario: Fork reduction is baseline-gated

- **WHEN** baseline probes show the suite wall time or peak daemon count justify a lower fork count
- **THEN** local `maxParallelForks` MAY be reduced to 4
- **AND** the reduction SHALL be recorded together with the measured baseline numbers

### Requirement: Real-Gradle suites reuse providers and sources services at class scope

Test fixtures that build real MCP servers and run real Gradle builds through a real `DefaultGradleProvider` SHALL share one class-scoped `GradleProvider` (and its `SourcesService`) across test methods so the session-view cache hits and repeated `mcpDependencyReport` builds are avoided. The shared provider and its `BuildManager` SHALL be owned outside the per-method server fixture: per-method server teardown SHALL NOT close them, they SHALL remain usable across successive per-method servers and across in-method server recreations, and the owning test class SHALL close them deterministically exactly once at class teardown (`@AfterAll`). Fixture classes that already run one server per class satisfy this requirement with their existing class-scoped lifecycle. Fixture classes that build real MCP servers but do not run real Gradle builds (their `GradleProvider` is a mock and no real dependency-report builds run) are exempt from the shared-provider mandate: they SHALL share only their `SourcesService` at class scope, and their per-method mock provider lifecycle SHALL remain unchanged.

#### Scenario: Shared provider survives per-method server teardown

- **WHEN** a fixture class that runs real Gradle builds builds a per-method server and runs several test methods against one class-scoped provider and sources service
- **THEN** each per-method server close SHALL NOT close the shared provider or its build manager
- **AND** the shared provider SHALL remain usable for subsequent test methods
- **AND** the shared provider SHALL be closed exactly once in `@AfterAll`

#### Scenario: In-method server recreation keeps shared components

- **WHEN** a test closes and recreates the server fixture within a test method
- **THEN** the class-scoped provider, build manager, and sources service SHALL survive the recreation
- **AND** the recreated server SHALL reuse the same shared instances

#### Scenario: Fixtures without real builds share only the sources service

- **WHEN** a fixture class builds real MCP servers but runs no real Gradle builds (its `GradleProvider` is the inherited relaxed mock and its `GradleDependencyService` is mocked, as in `GradleVersionResolutionIntegrationTest`)
- **THEN** the class SHALL share one class-scoped `SourcesService` across test methods
- **AND** its provider lifecycle SHALL remain per-method
- **AND** the class SHALL NOT be required to share or class-scope a `GradleProvider`

#### Scenario: Per-method provider still allowed where isolation is required

- **WHEN** a test genuinely requires a fresh provider per method (e.g., asserting provider lifecycle)
- **THEN** the test SHALL create and close the provider within the method
- **AND** the testing-standards real-provider mandate (real `DefaultGradleProvider`, not relaxed mocks) SHALL still apply
