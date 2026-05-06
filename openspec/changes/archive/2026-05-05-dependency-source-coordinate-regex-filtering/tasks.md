## 1. Filter Semantics

- [x] 1.1 Replace the current colon-split matcher with regex-based coordinate matching.
- [x] 1.2 Define and document the canonical coordinate form used by the matcher in both Gradle-side and service-side code paths.
- [x] 1.3 Add early validation for invalid regex input with a clear error message.

## 2. Pipeline Updates

- [x] 2.1 Update the Gradle init script path so candidate dependencies can be pruned with the same regex filter before source extraction.
- [x] 2.2 Update the service-layer verification path so final filtering uses the same regex semantics, coordinate normalization, and blank-filter normalization.
- [x] 2.3 Keep empty-scope handling separate from no-match handling so genuine empty scopes still return empty results with a visible note when a dependency filter was supplied.
- [x] 2.4 Keep the dependency regex in the session-view cache key while ensuring CAS identity, hashes, directories, locks, extraction markers, normalized paths, and indexes do not include the regex.
- [x] 2.5 Return a distinct dependency-source diagnostic when the regex matches dependencies but none have source artifacts.

## 3. Documentation

- [x] 3.1 Update dependency-source and `inspect_dependencies` tool descriptions to describe regex-based coordinate filtering.
- [x] 3.2 Add examples that show matching by group, artifact, version, and variant using regex.
- [x] 3.3 Call out invalid-regex failure behavior in the tool docs.
  - [x] 3.4 Document trusted Kotlin regex performance semantics, empty-scope notes, populated no-match errors, blank-filter handling, graph-wide transitive matching, and view-cache-vs-CAS boundaries.

## 4. Testing

- [x] 4.1 Add matcher tests for coordinate regex matching and invalid regex rejection.
- [x] 4.2 Add dependency-source tests that verify filtering stays consistent between Gradle-side pruning and service-side verification.
- [x] 4.3 Add no-match and empty-scope tests that confirm errors are only raised when the unfiltered scope contained dependencies, while genuinely empty scopes return an empty result with a note.
- [x] 4.4 Add source-service cache tests for filtered view keys, exact-key invalidation, CAS identity stability, and no-source diagnostics.
  - [x] 4.5 Cover blank-filter normalization and invalid regex failure without pinning generated schema prose.
- [x] 4.6 Run the relevant test suite, then `./gradlew check` if the touched surface requires broader validation.
