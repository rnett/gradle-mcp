# Capability: build-execution

## Purpose

Specifies how the GradleProvider captures Gradle Tooling API progress events and emits MCP progress notifications with numerical percentages.

## Requirements

### Requirement: Progress listener in `GradleProvider`

The `GradleProvider` SHALL include a `ProgressListener` that captures `StatusEvent` from the Gradle Tooling API.

#### Scenario: Capturing StatusEvent

- **WHEN** a Gradle build is started via `GradleProvider.runBuild`
- **THEN** the `GradleProvider` SHALL capture `StatusEvent` and update `RunningBuild` accordingly

### Requirement: Concurrency & Thread Safety

The build execution and state management system SHALL ensure thread safety across multiple contexts (Tooling API threads, progress callbacks, and MCP readers).

- **Atomic Operations**: Developers MUST use atomic operations for shared state (e.g., `ConcurrentHashMap.computeIfAbsent` over `getOrPut`).
- **Atomic Reference Cleanup**: Clearing an `AtomicReference` MUST use `compareAndSet(expected, null)` to prevent overwriting newer values.
- **Cross-Thread Visibility**: Status or cancellation flags read across thread contexts MUST be annotated with `@Volatile`.
- **Thread Confinement**: Stream-specific writers (e.g., `LineEmittingWriter`) SHOULD remain thread-confined to avoid unnecessary synchronization overhead.
- **Hot-Path Optimization**: In hot paths (e.g., log tail display, regex search), the system SHALL scan console output directly via index-based scanning of the underlying buffer to avoid massive allocations from `String.lines()`.

### Requirement: Structured Output Parsing

The system SHALL support unified parsing of internal Gradle output intercepted via init scripts.

- **Unified Format**: Internal logs MUST use the `[gradle-mcp] [category] ...` format for centralized parsing.
- **Sequential Parsing**: When parsing multiple bracketed fields, the system SHALL use sequential `substringAfter("]").trim()` calls to avoid ambiguity.
- **Regex Escaping**: Literal brackets in Kotlin `Regex` strings MUST be escaped (`\[`, `\]`) even within raw strings.

The system SHALL calculate and emit a numerical percentage for build progress via the MCP client.

#### Scenario: Emitting progress notification with percentage

- **WHEN** a progress event occurs during a build
- **THEN** the system SHALL calculate the percentage and emit a progress notification to the MCP client

### Requirement: Java Home Configuration during Execution

The build execution process SHALL configure the launcher with the resolved Java home before starting the build.

#### Scenario: Configured launcher with Java home

- **WHEN** a build is initiated
- **AND** a Java home path is resolved (either explicitly or via environment)

### Requirement: Task provenance extraction from TaskOperationDescriptor

The system SHALL extract task provenance information from the Tooling API's `TaskOperationDescriptor` during task execution.

#### Scenario: Extract provenance from TaskOperationDescriptor

- **WHEN** a task finishes via the Tooling API
- **AND** `TaskOperationDescriptor.getOriginPlugin()` returns a `BinaryPluginIdentifier` with a non-null `getPluginId()`
- **THEN** the system SHALL extract the plugin ID as provenance from the descriptor
- **AND** SHALL store it in the `TaskResult` model

#### Scenario: No provenance when API unavailable

- **WHEN** a task finishes via the Tooling API
- **AND** `TaskOperationDescriptor.getOriginPlugin()` returns null or a `ScriptPluginIdentifier` (no plugin ID)
- **THEN** the system SHALL NOT attempt to extract provenance
