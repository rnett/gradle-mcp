## Why

The current `inspect_build` tool is an overloaded "God Object" with 15+ flat, conditionally dependent parameters. This makes it difficult for LLMs to use effectively, as they must guess which combinations of parameters are valid for
specific tasks like viewing logs, checking tests, or monitoring builds.

## What Changes

- **REMOVAL**: The legacy `inspect_build` tool will be removed. (**BREAKING**)
- **NEW**: `query_build` tool for data retrieval (DASHBOARD, CONSOLE, TASKS, TESTS, FAILURES, PROBLEMS).
- **NEW**: `wait_build` tool for blocking logic and monitoring.
- **CONSOLIDATION**: A unified `outcome` enum replaces separate task and test outcome filters.
- **NEW CAPABILITY**: Intelligent Auto-Expansion in `query_build` (single match returns full details).
- **NEW CAPABILITY**: Regex filtering with line numbering for `kind=CONSOLE`.
- **NEW CAPABILITY**: "Wait + Tail" as a single operation in `wait_build`.

## Capabilities

### New Capabilities

- `build-querying`: Structured retrieval of build components (tasks, tests, failures, problems) with intelligent auto-expansion.
- `build-monitoring`: Blocking wait logic with automatic console tailing and automated inspection hints.
- `console-regex-filtering`: Regex-based filtering of build console logs with original line numbering and tail-first pagination.

### Modified Capabilities

- `test-progress-reporting`: Requirements change to include automated hints to `query_build` in monitoring outputs.
- `task-output-capturing`: Requirements change to integrate with the new `kind=TASKS` query logic.

## Impact

- **API**: Major breaking change to the build inspection surface.
- **Code**: Refactoring of `GradleBuildLookupTools.kt` and associated arg classes.
- **Docs**: Full update of `AGENTS.md` and tool-specific markdown documentation.
- **Tests**: All integration tests relying on `inspect_build` (e.g., `BackgroundBuildStatusWaitTest`) must be updated.
