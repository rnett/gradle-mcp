## Context

Current tools provide basic build and test information but lack granular feedback for complex scenarios like long-running tasks, deep test hierarchies, and large console outputs. The `inspect_build` tool's summary mode is too high-level,
and `captureTaskOutput` can be frustrating when it yields no results without explanation.

## Goals / Non-Goals

**Goals:**

- Provide actionable feedback when tasks are running or missing.
- Group test results by suite in summaries for better readability.
- Support "tail" mode in pagination to handle large log files.
- Refine symbol search to support precise FQN filtering.
- Ensure build summaries include actual error messages from failures.

**Non-Goals:**

- Rewriting the core Gradle Tooling API integration.
- Implementing a full interactive terminal emulator.
- Changing the public MCP tool signatures (input schemas).

## Decisions

### 1. Suite-Based Test Grouping

Group tests by their `suiteName` in `toOutputString`.

- **Rationale**: Large projects have hundreds of tests; a flat list is unreadable. Grouping provides immediate context on which classes are failing.
- **Alternatives**: Flat list with FQNs (current, too noisy).

### 2. Tail Mode Pagination

Extend the pagination metadata to explicitly support "tailing" (reading from the end).

- **Rationale**: Console logs are often thousands of lines; users usually want the *last* few lines first.
- **Implementation**: If `consoleTail=true`, calculate the range from the end and adjust metadata to show "last X lines".

### 3. Build Summary Error Context

Include the first few lines of actual failure messages in the `inspect_build` summary.

- **Rationale**: Just seeing "Build Failed" or a failure ID requires an extra tool call. Showing the message immediately often provides enough info to fix the issue.

### 4. FQN Non-Tokenized Search

In `DECLARATION` mode, treat the `fqn` field as a literal string (non-tokenized).

- **Rationale**: FQNs like `com.example.MyClass` should be searchable as a whole or with wildcards, rather than split by dots which causes false positives.

### 5. Running Task Awareness

Check `activeOperations` when a task output or result is requested but not found.

- **Rationale**: Distinguishes between "task doesn't exist" and "task is still running", preventing user confusion.

## Risks / Trade-offs

- [Risk] → Increased output size in summaries.
- [Mitigation] → Use strict limits (e.g., top 3 failures, top 20 tests) and leverage pagination.

- [Risk] → Breaking change to internal `TestResult` model.
- [Mitigation] → Update all call sites and test fixtures in a single pass.
