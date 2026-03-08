## Context

Currently, the Gradle MCP server reports progress by emitting the last received event's description. This leads to showing "Finished task: :path" when no other task has started, which can be misleading. In parallel builds, the progress
message only reflects one of many active tasks.

## Goals / Non-Goals

**Goals:**

- Provide a more accurate "at-a-glance" view of current Gradle activity.
- Support parallel execution visibility.
- Centralize progress message generation logic.

**Non-Goals:**

- Full "progress bar" implementation (handled by client).
- Test-level progress (e.g., "4/23 tests") - this is a future roadmap item.

## Decisions

### 1. Store Active Operations in `RunningBuild`

We will add a `MutableSet<String>` (using `LinkedHashSet` via `mutableSetOf()`) to `RunningBuild` to track active task paths and project configurations.

- **Rationale**: `RunningBuild` is the central state object. Using a `LinkedHashSet` preserves insertion order, allowing us to consistently display the first-started task as the "lead" operation.

### 2. Centralized Message Generation

Add a method to `RunningBuild` to generate a descriptive progress message.

- **Priority 1**: If `activeOperations` is not empty, use the first element as the lead task. If there are others, append " and N others".
- **Priority 2**: If `activeOperations` is empty but a task recently finished, show "Finished task: <lastTask>".
- **Priority 3**: Fallback to the raw event message.

### 3. Progressive Disclosure Suffix

`RunningBuild` will also track a `currentSubStatus` (from `StatusEvent` like "Downloading..."). If present, this is appended as a suffix to the primary task message.

## Risks / Trade-offs

- [Risk] → Increased memory usage in `RunningBuild` for tracking task paths.
- [Mitigation] → Tasks are removed from the set upon completion; only active tasks are stored.
- [Risk] → Race conditions in multi-threaded progress updates.
- [Mitigation] → Use `ConcurrentHashMap`-backed `MutableSet`.
