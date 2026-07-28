## Context

The `dependency-filtering` capability spec (in `openspec/specs/dependency-filtering/spec.md`) defines the behavioral contract for regex-based dependency filtering: the `dependency` parameter is a full-string Kotlin regex over canonical coordinates `group:name:version[:variant]`, with unresolved deps using `group:name` and project deps using `project::path`.

The implementation is already split into two layers:

1. **Init script** (`dependencies-report.init.gradle.kts`) — carries its own private static copies of `normalizeDependencyFilter`, `canonicalDependencyCoordinate`, `dependencyCoordinateCandidates`, and `matchesAnyDependencyCoordinate`. Performs authoritative filtering during dependency resolution, update-candidate selection, and source-candidate selection. The duplication is unavoidable (Gradle classloader boundary) and the functions are stable string-formatting utilities.

2. **Server side** (`DependencyFilterMatcher.kt` + `GradleDependencyService.kt`) — `DependencyFilterMatcher` is the canonical filter class wrapping a coordinate `Regex?` and a version `Regex?`. The service (`DefaultGradleDependencyService`) constructs it from `DependencyRequestOptions`, passes the raw filter strings to the init script via `-Pmcp.dependencyFilter=<value>` and `-Pmcp.versionFilter=<value>`, then applies `filterDependencyTree` on the parsed report as a server-side consistency pass.

The top-level functions in `DependencyFilterMatcher.kt` (`canonicalDependencyCoordinate`, `dependencyCoordinateCandidates`, `normalizeDependencyFilter`, `GradleDependency.canonicalCoordinateCandidates`) are shared utilities used by the service, tools, and the out-of-scope `SourcesService.SourceDependencyFilter`.

`GradleDependencyTools` (inspect, updates, reports) consumes the already-filtered report from the service and performs no independent filtering.

## Goals / Non-Goals

**Goals:**
- Add spec coverage for the server-side post-processing contract: `DependencyFilterMatcher` as the canonical filter, `filterDependencyTree` with parent-preservation semantics.
- Add targeted unit tests for `filterDependencyTree` tree-pruning behavior.
- Document the two-layer architecture (init-script authoritative + server-side consistency) in the spec.

**Non-Goals:**
- No changes to the init script (classloader boundary).
- No changes to `DependencySourceTools.kt` or `SourcesService.SourceDependencyFilter` (being moved to a separate MCP).
- No changes to tool input schemas, output formats, or `DependencyRequestOptions`.
- No changes to how filter strings are passed to the init script.
- No changes to the existing top-level functions in `DependencyFilterMatcher.kt`.
- No behavioral changes — the implementation is already correct and complete.

## Decisions

### Decision 1: Version filter in `DependencyFilterMatcher` — Already implemented

**Status**: Done. `DependencyFilterMatcher` already accepts `versionFilterRegex: Regex?` (default `null`). Its `matches(dep)` method checks `latestVersion ?: version` against the version regex when present.

**Rationale**: Combined coordinate + version filtering in a single `matches()` call is the correct design — both operate on the same dependency tree traversal. A separate `VersionFilterMatcher` would add indirection with no benefit.

### Decision 2: Server-side `filterDependencyTree` — Already implemented

**Status**: Done. `DefaultGradleDependencyService.filterDependencyTree` recursively walks `GradleDependency` nodes, pruning non-matching subtrees. A node is kept if `matcher.matches(dependency)` or `filteredChildren.isNotEmpty()` (parent preservation). Non-matching children of matching parents are pruned — consistent with the spec's "Graph-Wide Matching Without Implicit Closure" requirement.

**Resolved**: `filterDependencyTree` stays as a private function in the service. Tree walking is a report-transformation concern, not a matching concern. `DependencyFilterMatcher` remains a pure predicate (`matches(dep): Boolean`).

### Decision 3: Tools-layer filtering — Implicit, no additional work needed

**Status**: Correct as-is. `GradleDependencyTools` consumes the filtered report from the service. `findUpdates()` and `formatUpdatesSummary()` walk the pruned tree and only see matching nodes. Adding redundant filtering would be defense-in-depth theater with a maintenance cost.

## Risks / Trade-offs

- **[Low] Spec/code drift**: The spec doesn't document the server-side pass, so a developer could misunderstand why both layers exist. Mitigation: the spec update makes the two-layer architecture explicit.
- **[Low] No tree-pruning tests**: `DependencyFilterMatcherTest` covers the predicate but not `filterDependencyTree` walking behavior. Mitigation: add targeted tests.
- **[Accepted] Init-script duplication**: The duplicated functions are unavoidable (classloader boundary). They are stable format utilities unlikely to diverge.
- **[None] Performance**: The post-processing pass is negligible compared to the Gradle build.

## Affected Files

| File | Change |
|------|--------|
| `openspec/specs/dependency-filtering/spec.md` | Add requirement: server-side post-processing contract |
| `openspec/changes/regex-dependency-filter/specs/dependency-filter-matcher/spec.md` | Create delta spec for the new capability |
| `openspec/changes/regex-dependency-filter/specs/dependency-filtering/spec.md` | Create delta spec for the modified capability |
| `openspec/changes/regex-dependency-filter/tasks.md` | Create task list |
| Test files | Add `filterDependencyTree` unit tests |
| Production code | No changes |