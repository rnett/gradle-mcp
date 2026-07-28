## Why

The `DependencyFilterMatcher` class and `filterDependencyTree` post-processing pass in `GradleDependencyService` are already implemented: the matcher supports combined coordinate + version regex filtering, the service applies tree-pruning after parsing the init script output. However, this server-side filtering layer is not documented in the `dependency-filtering` spec, and the tree-pruning behavior has no dedicated unit test coverage. The spec also does not explain the two-layer architecture (init-script authoritative filter + server-side consistency pass).

This change adds the missing spec requirements, delta specs, and test coverage to complete the consolidation. Production code does not need changes.

## What Changes

- **Spec update**: Add a new requirement to `openspec/specs/dependency-filtering/spec.md` covering the server-side post-processing contract, `DependencyFilterMatcher` as the canonical filter, `filterDependencyTree` with parent-preservation semantics, and the two-layer architecture.
- **Delta specs**: Create `specs/dependency-filter-matcher/spec.md` for the new capability and `specs/dependency-filtering/spec.md` with an ADDED requirement for the modified capability.
- **Test coverage**: Add unit tests for `filterDependencyTree` behavior: parent preservation for matching transitive children, child pruning for non-matching children of matching parents, version-filter interaction, and edge cases (empty tree, all-match, no-match).

## Capabilities

### New Capabilities
- `dependency-filter-matcher`: Canonical `DependencyFilterMatcher` server-side filter class supporting combined coordinate-regex and version-regex matching, wired into `GradleDependencyService` as a post-processing pass with tree-pruning semantics.

### Modified Capabilities
- `dependency-filtering`: Add a requirement documenting the server-side post-processing contract — `DependencyFilterMatcher` as the canonical filter, `filterDependencyTree` with parent-preservation semantics, and the two-layer architecture.

## Impact

- **`openspec/specs/dependency-filtering/spec.md`**: New requirement for server-side post-processing.
- **`openspec/changes/regex-dependency-filter/`**: Delta specs, tasks, corrected artifacts.
- **Test files**: New unit tests for `filterDependencyTree` behavior.
- **Production code**: No changes (already implemented correctly).
- **`DependencySourceTools.kt`, `SourcesService.SourceDependencyFilter`**: Explicitly excluded (being moved).