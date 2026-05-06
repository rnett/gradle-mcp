## Why

The current dependency-source filter logic uses colon-splitting and name-prefix heuristics to approximate coordinate matching. That makes the behavior hard to predict, duplicates logic across the Gradle-side prefilter and the service-side verifier, and creates special cases that are difficult to explain.

Regex-based filtering against dependency coordinates is more explicit and more consistent. It gives users a single matching model, keeps the filter syntax flexible, and avoids the current hacky part-count rules.

## What Changes

- Replace the current colon-part dependency matcher with a regex-based matcher that operates on canonical dependency coordinates.
- Apply the same regex semantics in both the Gradle prefilter and the in-memory service-layer verification so the filtering rules stay aligned.
- Keep the scope model unchanged: project, configuration, and source set selection still determine the candidate dependency set before regex filtering is applied.
- Validate invalid regex input early and return a clear error message instead of silently falling back to partial matching.
- Keep regex filters in the session-view cache key while keeping them out of CAS identity, hashes, paths, locks, extraction markers, normalized paths, and source indexes.
- Return empty results with a visible note for genuine empty scopes, scoped no-match errors for populated scopes with zero regex matches, and a distinct no-source diagnostic when matched dependencies have no source artifacts.
- Update tool descriptions and examples so agents understand that the filter is a coordinate regex, not a colon-delimited shortcut syntax.

## Capabilities

### New Capabilities

- `dependency-source-coordinate-regex-filtering`: Coordinate-based regex filtering for dependency source tools and `inspect_dependencies`.

### Modified Capabilities

- `dependency-source-search`: Dependency source selection now uses regex matching over coordinates instead of ad hoc coordinate parsing.
- `project-dependency-tools`: `inspect_dependencies` filtering and update-check candidate selection use the same coordinate regex semantics.

## Impact

- `DependencyFilterMatcher.kt`: Replace the current split-and-prefix matcher with a regex matcher over canonical coordinates.
- `SourcesService.kt` and `GradleDependencyService.kt`: Thread the regex filter through both filtering stages, keep the early-pruning path aligned with service-side validation, and keep view-cache behavior separate from CAS identity.
- `dependencies-report.init.gradle.kts`: Update the Gradle-side filtering logic to evaluate the same regex against resolved dependency coordinates.
- Tool descriptions for dependency-source tools and `inspect_dependencies`: Document the new regex syntax with examples, blank-filter handling, empty-scope notes, and invalid-input guidance.
- Tests: Add coverage for coordinate matching, invalid regex handling, no-match behavior, no-source diagnostics, view-cache/CAS boundaries, and consistency between Gradle-side and service-side filtering.
