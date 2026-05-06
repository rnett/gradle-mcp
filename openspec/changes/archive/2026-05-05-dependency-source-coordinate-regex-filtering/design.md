## Context

The existing dependency filter accepts a colon-delimited string and then applies a mix of exact and prefix matching depending on how many parts were supplied. That behavior is convenient for a narrow set of cases, but it is not a stable rule set. It also requires separate logic in the Gradle init script and in the service layer, which makes the current implementation hard to reason about.

The new behavior should treat dependency coordinates as the source of truth. A regex applied to the coordinate string is easier to describe, easier to test, and easier to keep consistent across the pipeline.

## Goals / Non-Goals

**Goals:**

- Match dependency source tools and `inspect_dependencies` using a regex against canonical coordinates.
- Keep Gradle-side pruning and service-side verification on the same matching model.
- Surface invalid regex input clearly and early.
- Preserve the existing scope resolution model for project, configuration, and source set selection.

**Non-Goals:**

- Introduce a separate query language or glob syntax for dependency filtering.
- Change how dependency scopes are resolved before filtering.
- Add new search features unrelated to coordinate dependency filtering.
- Replace Kotlin/JVM regex semantics with a reduced regex engine or reject valid regexes for performance reasons.

## Decisions

### Decision 1: Match against canonical coordinates

The filter will be evaluated against a canonical coordinate string built from the resolved dependency fields, rather than against colon-separated input parts.

Chosen coordinate shape:

- `group:name:version` for the common resolved form
- `group:name:version:variant` when variant information is available in the service layer
- `group:name` for unresolved declared dependencies that have no selected version or variant
- `project::path` for project dependencies, where `path` is the Gradle project path

This keeps the matching target predictable while still allowing regexes that account for optional variants. Blank filters are normalized to absent at tool and service boundaries before Gradle invocation.

### Decision 2: Compile regexes at matching boundaries

The Gradle init script and the service layer receive the same normalized filter string, then compile it to `Regex` at the boundary where matching happens. Low-level dependency matching receives a compiled `Regex?` instead of owning raw-string normalization.

Rationale:

- The Gradle layer can prune candidates early.
- The service layer can perform the final verification with the richer dependency model.
- Using the same raw regex avoids a second, translated filter syntax.

### Decision 3: Reject invalid regex early

If the filter cannot be compiled as a regex, the system should fail fast with a clear message before doing expensive dependency work.

Rationale:

- Prevents confusing partial behavior.
- Avoids spending time resolving and indexing dependencies for an invalid request.

### Decision 4: Preserve empty-scope behavior with a visible note and separate no-source diagnostics

If a scope genuinely resolves no dependencies, the tools should continue to behave like an empty scope rather than converting that case into a filtering error. When a dependency filter was supplied, the response should include a visible note explaining that the selected scope contained no dependency sources and the filter had no candidates to match.

If the selected scope contains dependencies but the regex matches none, the tools should return a scoped no-match error. If the regex matches dependencies but none of the matched dependencies has source artifacts, dependency-source tools should return a distinct no-sources diagnostic. For `inspect_dependencies`, empty filtered scopes should surface a note in the report/update summary.

Rationale:

- A regex filter should only report a no-match error when the unfiltered scope had candidates.
- A visible note avoids a confusing successful empty response without incorrectly treating the empty scope as a filtering failure.
- A distinct no-sources diagnostic tells the user that the regex was correct but there are no source artifacts to show.

### Decision 5: Keep dependency filters in the view cache key, not CAS

Filtered source calls return different source views, so the dependency regex belongs in the session-view cache key together with the selected scope. The dependency regex must not participate in CAS identity, CAS hashes, CAS directory names, lock paths, extraction markers, normalized paths, or source indexes.

Rationale:

- Session views are request-shaped and can legitimately differ by filter.
- CAS entries are dependency-artifact-shaped and must remain stable regardless of how a dependency was selected.
- `fresh` and `forceDownload` should invalidate the exact requested view while refreshing only the selected dependencies for that call.

### Decision 6: Reuse cached session views with bounded idle eviction

Materialized session-view directories remain immutable, but later calls may reuse a cached view for the same scope and dependency-filter key. The in-memory cache uses Caffeine, is bounded to 128 keys with a 30-minute idle TTL, and session-view locks are bounded independently from cache-key cardinality.

Rationale:

- Reuse preserves the immutable-view model while avoiding unnecessary rematerialization work.
- A bounded idle cache prevents unbounded growth from high-cardinality filters.
- Bounded lock striping prevents failed or high-cardinality filters from growing a per-filter lock map.

### Decision 7: Preserve graph-wide matching when transitives are requested

When `onlyDirect=false`, a dependency filter can match a transitive node independently even if its parent does not match. Matching a node does not implicitly include its children unless they also match the regex.

## Risks / Trade-offs

- Regexes are more powerful than the current heuristic syntax, so users can write broader or more expensive patterns.
- Matching on canonical coordinates instead of the current part-count rules may change a few edge-case matches.
- The Gradle init script still has a smaller dependency view than the service layer, so the service-layer verifier remains necessary.
- Arbitrary Kotlin regex performance remains the caller's responsibility because filters are trusted input; complex patterns may be expensive.
