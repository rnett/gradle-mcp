# Capability: build-output-concurrency

## Purpose

Specifies the concurrency architecture of the Gradle build output processing pipeline, ensuring thread safety and performance during multi-threaded output delivery and concurrent MCP reads.

## Requirements

### Requirement: Four Thread Contexts

The system SHALL handle the following four thread contexts interacting with shared build state:

- **Stdout delivery**: Single thread delivering daemon stdout.
- **Stderr delivery**: Single thread delivering daemon stderr (separate from stdout).
- **Progress callbacks**: Multi-threaded Gradle Tooling API callbacks (especially with `--parallel`).
- **MCP readers**: Concurrent coroutine dispatcher threads reading build state (e.g., `inspect_build`).

### Requirement: Thread-Confined Writers

Writers responsible for buffering individual output streams SHALL remain thread-confined to avoid synchronization overhead.

- `LineEmittingWriter` MUST NOT use internal synchronization.
- It SHALL use plain `StringBuilder` and non-atomic flags.
- **Rationale**: Each stream is delivered by exactly one dedicated Tooling API thread.

### Requirement: Atomic Console Output Mutations

The system SHALL ensure that console output mutations are atomic across stdout and stderr streams.

- `RunningBuild.addLogLine()` MUST be a single-operation mutation.
- It SHALL use `StringBuffer` to provide per-method synchronization for concurrent `appendLine` calls from stdout/stderr threads.

### Requirement: Concurrent State Management

Shared build state (task results, problems, failures) MUST use thread-safe collections.

- **Task Results**: SHALL use `ConcurrentHashMap` with atomic `computeIfAbsent` for lazy initialization.
- **Progress State**: SHALL use concurrent collections and `@Volatile` flags for cross-thread visibility.
- **Problems & Failures**: SHALL use `ConcurrentHashMap.newKeySet()` for safe concurrent iteration.

### Requirement: Hot-Path Performance

Output access patterns SHALL avoid expensive materialization of large log buffers.

- **Hot-Path Scanning**: Tail display, head display, and regex search MUST scan the underlying console output buffer directly using index-based scanning (`indexOf`, `lastIndexOf`) instead of allocating lists of strings via `String.lines()`.
- **Atomic Snapshots**: `consoleOutput.toString()` MUST provide a consistent snapshot of the buffer.

### Requirement: Parallel MCP Message Processing

The MCP server SHALL launch incoming tool handlers in independent coroutines.

- It MUST NOT process tool calls sequentially on the transport thread.
- **Rationale**: Long-running builds (e.g., `gradle()` awaiting completion) must not block concurrent status checks (e.g., `inspect_build()`).

## Design & Rationale

### Concurrency Primitives

- **Single-writer, no concurrent readers**: No concurrency control needed (thread confinement).
- **Single-writer + concurrent readers**: Visibility guarantees needed (`@Volatile`, `StringBuffer.toString()`)
- **Multiple writers + concurrent readers**: Thread-safe collections needed (`ConcurrentHashMap`, `StringBuffer`).

### Console Output Scanning

For large builds (e.g., `--info` or `--debug`), allocating a `String` for every line causes massive GC pressure. Scanning the buffer directly is O(n) in characters but O(1) in object allocations.

- **Tail**: Scan backwards from the end using `lastIndexOf('\n', ...)`.
- **Total Line Count**: Count newline characters directly: `output.count { it == '\n' }`.
