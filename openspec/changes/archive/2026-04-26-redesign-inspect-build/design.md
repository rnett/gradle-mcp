## Context

The `inspect_build` tool currently acts as a catch-all for build introspection, leading to a complex parameter schema. The current implementation uses a single data class (`InspectBuildArgs`) and a large `tool` block that branches
internally based on `mode`, `testName`, `taskPath`, etc.

## Goals / Non-Goals

**Goals:**

- Separate "Querying" (static data) from "Waiting" (blocking/async events).
- Implement an "Auto-Expand" heuristic to reduce explicit configuration.
- Standardize console log filtering with regex support.
- Simplify outcome filtering across tasks and tests.

**Non-Goals:**

- Modifying the underlying Gradle Tooling API integration.
- Changing how data is collected during the build (only changing how it is queried).
- Adding new data types (e.g., project dependencies) to the inspection tools.

## Decisions

### 1. Two-Tool Split

**Rationale**: Waiting for a build is a fundamentally different interaction pattern (long-running, async) than querying a task's outcome (instant, synchronous). Separating them into `wait_build` and `query_build` allows for cleaner
parameter sets.
**Alternatives**: Keeping one tool with an `action` enum (rejected due to parameter pollution even with enums).

### 2. Intelligent Auto-Expansion

**Rationale**: Users often want to see "the error" or "the details" of a specific failure. If a query (e.g., `query=":app:test"`) uniquely identifies one component, returning the summary list is an unnecessary extra step.
**Logic**:

- Perform the filter/lookup.
- If `results.size == 1`, call the detail-rendering logic.
- Otherwise, call the summary-list-rendering logic and append a hint.

### 3. Unified Outcome Enum

**Rationale**: `TaskOutcome` and `TestOutcome` share concepts (SUCCESS/PASSED, FAILED). A single enum `BuildComponentOutcome` simplifies filtering queries for the LLM.
**Mapping**:

- `SUCCESS` / `PASSED` → `PASSED`
- `FAILURE` / `FAILED` → `FAILED`
- Others map naturally or are ignored if not applicable to the component type.

### 4. Console Regex Filtering with Line Numbering

**Rationale**: Finding a needle in a 10k line Gradle log is hard. Regex filtering makes it possible. Line numbering is essential for the LLM to understand context if it needs to read around the match later.
**Implementation**:

- Scan `consoleOutput`.
- Maintain a line counter.
- Yield matching lines with `lineNum: content` format.

## Risks / Trade-offs

- **[Risk]** Breaking existing skill/agent logic that relies on `inspect_build`. → **Mitigation**: Update all built-in skills and documentation immediately.
- **[Risk]** Auto-expansion returning too much data (e.g., a task with massive console output). → **Mitigation**: Respect pagination `limit` even in auto-expand mode for console output.
- **[Risk]** Ambiguity in `query` field. → **Mitigation**: Clear instructional hints in the tool response when matches are > 1.
