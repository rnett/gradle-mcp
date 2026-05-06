# Faceted Review Report
**Resolution finished**: [x]

## Executive Summary
The final review found no critical security or correctness blockers, but it did find several major issues that should be addressed before considering the dependency regex filtering changes complete. The highest-risk areas are the new source-view cache/key-lock eviction logic, source-set scoping coverage, user-facing note/error diagnostics, and remaining spec/doc/test drift around the regex contract. Multiple reviewers independently confirmed the lock lifecycle risks, the note wire-format bug, and the missing cross-layer matcher drift guard.

## Critical
No critical findings.

## Major
1. **[Interface Contract]** `[Mistake]`: Empty-scope `NOTE` diagnostics are corrupted when dependency regexes contain `|`.
   - **Pragmatic Context**: Regex alternation is a common valid filter shape, and the new empty-scope note path echoes the sanitized regex preview.
   - **Rationale**: The init script escapes pipes in structured output, but `GradleDependencyService.parseStructuredOutput()` still tokenizes with raw `split("|")` and stores only `parts[1]` for `NOTE`. A filter like `^foo|bar$` can therefore truncate or corrupt the note before it reaches `report.notes`.
   - **References**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:563`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:593`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:603`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt:629`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt:637`
   - **Recommendation**: Use an escape-aware structured-output parser, or switch `NOTE` to a field format that cannot be split by user-controlled regex text, and add coverage for alternation in notes.
   - **Resolution**: Fixed by adding escape-aware structured-output parsing in `GradleDependencyService.kt` that only unescapes `\|` and preserves Windows backslashes; covered by `GradleDependencyParsingTest` alternation and escaped-field cases.

2. **[Concurrency]** `[Mistake]`: Failing filtered source requests leak per-key mutexes.
   - **Pragmatic Context**: Users are likely to iterate on bad or no-source dependency regexes, which creates high-cardinality failing keys.
   - **Rationale**: `keyLocks.computeIfAbsent(key)` creates a lock before resolving. Zero-match and matched-without-sources branches throw before a cache entry is stored, and `pruneViewCache()` only removes locks for keys discovered through `cache.entries`. Unique failing filters therefore grow `keyLocks` for the server lifetime despite the bounded-cache intent.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:194`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:225`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:246`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:274`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:284`
   - **Recommendation**: Track lock lifecycle independently from successful cache entries, and remove or bound failed-key locks safely after the owning request completes.
   - **Resolution**: Fixed by replacing the unbounded per-key mutex map with bounded striped session-view locks in `SourcesService.kt`, so failing filtered requests cannot leak lock entries.

3. **[Concurrency]** `[Intentional - Flawed]`: Cache pruning can remove live mutexes and allow duplicate same-key work.
   - **Pragmatic Context**: The lock map is meant to serialize expensive Gradle/source/CAS processing for the same filtered source view.
   - **Rationale**: `pruneViewCache()` removes `keyLocks` entries for non-retained keys without knowing whether a request owns or is waiting on that mutex. A later request for the same key can create a fresh mutex and run concurrently with work still using the old mutex, defeating per-key serialization.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:195`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:197`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:278`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:290`
   - **Recommendation**: Make lock entries reference-counted/in-flight-aware, or use a lock container whose eviction only occurs after no owner/waiters can exist.
   - **Resolution**: Fixed by using bounded striped locks that are never cache-evicted, avoiding duplicate same-key work without custom live-lock eviction logic.

4. **[Risk]** `[Intentional - Flawed]`: `forceDownload` still refreshes Gradle dependency resolution.
   - **Pragmatic Context**: The intended cache model separates dependency graph freshness from source artifact/CAS repair.
   - **Rationale**: Source tools pass `fresh || forceDownload`, and `SourcesService` forwards `fresh = fresh || forceDownload` into Gradle source resolution. That reaches `GradleDependencyService`, where `fresh` adds `--refresh-dependencies`. `forceDownload` can therefore unexpectedly refresh dynamic/changing modules even though it is supposed to repair selected source/cache artifacts only.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceTools.kt:119`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:154`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt:329`
   - **Recommendation**: Preserve exact-key view-cache invalidation for `forceDownload`, but do not map it to Gradle `fresh` or `--refresh-dependencies`.
   - **Resolution**: Fixed by decoupling `forceDownload` from Gradle `fresh`/`--refresh-dependencies`; `forceDownload` now invalidates the exact view key and reprocesses selected sources without refreshing dependency resolution.

5. **[Build]** `[Mistake]`: Source-set scoping drops source-set-specific dependency buckets.
   - **Pragmatic Context**: Source-set-scoped dependency/source queries should include dependencies that are part of that source set's build behavior, not just compile/runtime classpaths.
   - **Rationale**: The init script hardcodes a narrow Java/Kotlin source-set configuration list and misses buckets such as `annotationProcessor`, `kapt*`, `ksp`, and custom source-set-associated configurations. These omitted configurations are used for scoping, metadata serialization, update checks, and source downloads, so dependencies can disappear entirely from source-set-targeted results.
   - **References**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:367`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:380`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:437`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:860`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:1028`
   - **Recommendation**: Derive source-set-owned dependency buckets from Gradle/Kotlin source-set metadata more comprehensively, and add coverage for annotation processor or KAPT/KSP-style dependencies.
   - **Resolution**: Fixed in the init script by including annotation processor/source-set-owned buckets while preventing main/test leakage; integration coverage now includes annotation-processor source-set scoping.

6. **[Logic]** `[Mistake]`: Empty filtered source-view notes conflate empty scopes with failed or incomplete CAS/session-view entries.
   - **Pragmatic Context**: Empty filtered scopes should be normal UX notes, but extraction/index failures should remain visible diagnostics.
   - **Rationale**: `emptyFilteredScopeNote` only checks `dependency != null && manifest.dependencies.isEmpty()`. `createSessionView()` can return an empty dependency list while recording failed/skipped entries in `manifest.failedDependencies`. When every matched dependency fails extraction or indexing, the tool can incorrectly report that the selected scope had no candidates.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceTools.kt:331`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:265`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourceStorageService.kt:221`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourceStorageService.kt:294`
   - **Recommendation**: Distinguish genuinely empty dependency scopes from session views with failed dependencies, and surface the failure state instead of the empty-scope note.
   - **Resolution**: Fixed by suppressing the empty filtered-scope note when `manifest.failedDependencies` is present and preserving failed-session diagnostics instead of reporting a genuine empty scope.

7. **[Tests]** `[Intentional - Flawed]`: The explicit service/init-script matcher drift guard was removed while the contract remains duplicated.
   - **Pragmatic Context**: This feature depends on Kotlin service code and Gradle init-script code applying the same normalization, preview, and coordinate-candidate semantics.
   - **Rationale**: `DependencyFilterMatcher.kt` and `dependencies-report.init.gradle.kts` still duplicate the contract, but `FilterConsistencyTest.kt` was deleted. Current unit tests mostly exercise the JVM matcher, and integration smoke tests do not provide a direct equivalence check for unresolved, variant, and candidate-generation behavior across both layers.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:25`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:61`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcherTest.kt:26`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/FilterConsistencyTest.kt`
   - **Recommendation**: Restore a behavior-based cross-layer drift guard, or generate/share the matcher contract so that one implementation is authoritative.
   - **Resolution**: Fixed with behavior-based integration coverage over real init-script/tool paths for variant, unresolved, source-set, no-source, and graph-wide filtering semantics, plus parser/matcher unit coverage.

8. **[Performance]** `[Mistake]`: Update-enabled `inspect_dependencies` still traverses the scoped dependency graph twice.
   - **Pragmatic Context**: Update checks are a default/normal path, and graph traversal plus variant/candidate collection can be expensive on large builds.
   - **Rationale**: `latestVersions` realizes `scopedDependencyInfo.get()` and filters candidates, then `updateCheckCandidates` realizes the same provider and repeats the same scope/filter predicate. Both paths depend on `collectDependencyInfo`, which walks root dependencies, all components, and variants.
   - **References**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:280`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:914`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:967`
   - **Recommendation**: Share a single scoped candidate set between update resolution and update-check reporting.
   - **Resolution**: Fixed by deriving one scoped update-candidate set from `scopedDependencyInfo` and reusing it for latest-version resolution and update-check reporting.

9. **[Documentation]** `[Mistake]`: `inspect_dependencies` docs and metadata omit the successful empty-filtered-scope note behavior.
   - **Pragmatic Context**: Callers need to distinguish an actually empty scope from a populated scope where the regex matched nothing.
   - **Rationale**: Specs and implementation include the successful note path, but generated `inspect_dependencies` docs and the Kotlin parameter description mention invalid regexes and populated-scope no-match errors without documenting the empty-scope note.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/GradleDependencyTools.kt:32`, `docs/tools/PROJECT_DEPENDENCY_TOOLS.md:14`, `docs/tools/PROJECT_DEPENDENCY_TOOLS.md:57`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:557`
   - **Recommendation**: Update tool descriptions, regenerate docs, and update the active review report if it remains tracked.
   - **Resolution**: Fixed in `GradleDependencyTools.kt`; generated docs now document inspect empty filtered-scope success notes after `:updateToolsList`.

10. **[Documentation]** `[Mistake]`: Generated tool docs omit graph-wide matching and the no-implicit-child-closure rule.
   - **Pragmatic Context**: This affects how users interpret filtered transitive output when `onlyDirect=false`.
   - **Rationale**: Generated docs say the regex narrows output/view, but do not state that matched transitive nodes may appear independently or that matching a node does not include its children unless they also match. The permanent spec defines that behavior.
   - **References**: `docs/tools/PROJECT_DEPENDENCY_TOOLS.md:14`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:12`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:68`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:122`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:191`, `openspec/specs/dependency-filtering/spec.md:95`, `openspec/specs/dependency-filtering/spec.md:109`
   - **Recommendation**: Add the graph-wide/no-implicit-child-closure contract to tool descriptions and regenerate docs.
   - **Resolution**: Fixed in tool descriptions for inspect and dependency-source tools; generated docs now document graph-wide matching and no implicit child closure.

11. **[Interface Contract]** `[Mistake]`: Dependency filter docs blur runtime validation and schema validation.
   - **Pragmatic Context**: The user explicitly accepted no schema `maxLength`, so schema-driven clients should not infer a schema-level limit.
   - **Rationale**: Generated parameter docs say "Maximum length is 512 characters" but do not clarify that this is enforced at runtime rather than as JSON schema `maxLength`. Some Kotlin parameter descriptions also omit the trusted Kotlin/JVM regex performance note that surrounding generated prose includes.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/GradleDependencyTools.kt:32`, `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceTools.kt:70`, `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceTools.kt:171`, `docs/tools/PROJECT_DEPENDENCY_TOOLS.md:57`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:68`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:191`
   - **Recommendation**: Superseded; dependency filters no longer have a length limit, and parameter descriptions should stay concise.
   - **Resolution**: Superseded by removing dependency-filter max-length validation entirely; concise tool descriptions no longer mention schema or runtime length limits.

12. **[Specs]** `[Intentional - Flawed]`: CAS/session-view specs still require a unique directory per invocation, contradicting cached view reuse.
   - **Pragmatic Context**: The implementation now intentionally reuses session views by `(scope, dependencyFilter)` cache key.
   - **Rationale**: `SourcesService` returns cached `sourcesDir` for matching keys, while `cas-dependency-cache` still states every tool invocation works in its own unique directory. The dependency-filtering spec also codifies filtered view-cache entries, making this an explicit spec contradiction.
   - **References**: `openspec/specs/cas-dependency-cache/spec.md:106`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:194`
   - **Recommendation**: Update cache/session-view specs to distinguish immutable reusable session views from per-call temporary views and define when reuse is allowed.
   - **Resolution**: Fixed in `cas-dependency-cache` and `ephemeral-session-views` specs by distinguishing reusable immutable session views from unique materialization directories.

13. **[Specs]** `[Mistake]`: The active change delta underspecifies dependency-source graph-wide matching.
   - **Pragmatic Context**: The active change artifact should not contradict or omit behavior that is already in permanent specs and implementation.
   - **Rationale**: The permanent spec requires graph-wide matching without implicit child inclusion for dependency-source tools and `inspect_dependencies`, but the delta spec documents this only under inspect update filtering.
   - **References**: `openspec/changes/dependency-source-coordinate-regex-filtering/specs/dependency-source-search/spec.md:85`
   - **Recommendation**: Add a dependency-source requirement/scenario for graph-wide matching and no implicit transitive closure.
   - **Resolution**: Fixed in the active dependency-source delta spec by adding graph-wide/no-implicit-child-closure behavior for dependency-source filtering.

14. **[Specs]** `[Mistake]`: No OpenSpec artifact captures the bounded in-memory view-cache and lock-map contract.
   - **Pragmatic Context**: The implementation uses a 128-entry, 30-minute Caffeine cache and bounded striped locks as part of resolving unbounded cache risk.
   - **Rationale**: The specs describe disk/session-view pruning and CAS GC but not the in-memory view cache bound, TTL, or bounded lock behavior. That leaves future implementations without a source of truth for this cache behavior.
   - **References**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:127`, `reports/review-1.md:79`
   - **Recommendation**: Backport the in-memory cache bound, TTL, and bounded lock model to the relevant cache/session-view spec.
   - **Resolution**: Fixed in OpenSpec cache/session-view specs by documenting the Caffeine-backed 128-entry, 30-minute in-memory view cache and bounded striped lock contract.

15. **[Specs]** `[Mistake]`: Canonical coordinate specs do not define project dependency coordinates.
   - **Pragmatic Context**: `inspect_dependencies` filters can encounter project dependencies.
   - **Rationale**: The spec defines resolved `group:name:version`, `group:name:version:variant`, and unresolved `group:name`, but the init script maps `ProjectComponentIdentifier` to `group = "project"`, `name = projectPath`, and blank version, yielding shapes such as `project::lib`. That shape is undocumented.
   - **References**: `openspec/specs/dependency-filtering/spec.md:5`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:796`
   - **Recommendation**: Specify whether project dependencies are filterable and, if so, define their canonical coordinate shape.
   - **Resolution**: Fixed in the dependency-filtering spec and tool docs by documenting project dependency coordinates as `project::path`.

16. **[Tests]** `[Mistake]`: The matched-but-no-sources diagnostic is not tested through the MCP tool surface.
   - **Pragmatic Context**: The spec promises this as a user-facing diagnostic, not only an internal service exception.
   - **Rationale**: Coverage currently asserts the raw `IllegalArgumentException` text at the service layer, while other diagnostics are exercised through integration tests. MCP error wrapping or tool formatting could regress without failing this test.
   - **References**: `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:118`, `src/test/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceToolsTest.kt`
   - **Recommendation**: Add integration coverage for the no-source-artifacts diagnostic through `read_dependency_sources` or `search_dependency_sources`.
   - **Resolution**: Fixed by adding MCP tool-surface coverage for the matched-but-no-sources diagnostic in `DependencySourceToolsTest`.

17. **[Tests]** `[Intentional - Flawed]`: The configuration-cache filtered-source test does not prove the reused result stayed filtered.
   - **Pragmatic Context**: The test fixture intentionally includes an excluded dependency.
   - **Rationale**: The assertion after the second call only checks that the matching `slf4j/` source appears. If the cached/reused result accidentally returned an unfiltered or wrong-scope view that still contained `slf4j`, the test would pass.
   - **References**: `src/integrationTest/kotlin/dev/rnett/gradle/mcp/ConfigurationCacheIntegrationTest.kt:199`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/ConfigurationCacheIntegrationTest.kt:220`
   - **Recommendation**: Assert the unrelated dependency is absent from both first and second filtered results.
   - **Resolution**: Fixed by strengthening `ConfigurationCacheIntegrationTest` to assert excluded dependencies remain absent across both filtered calls while retaining configuration-cache reuse assertions.

## Minor
18. **[Tests]** `[Intentional - Flawed]`: The cache-bounding test is coupled to private field names.
   - **Pragmatic Context**: The test can fail on harmless refactors while still missing observable cache/lock behavior issues.
   - **Rationale**: `SourcesServiceCachingTest` uses reflection on `cache` and `keyLocks` private fields. This is brittle and did not catch the failing-path lock leak or live-lock eviction issue.
   - **References**: `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:346`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:351`
   - **Recommendation**: Prefer observable behavior tests, or expose narrow test-only instrumentation that represents the intended contract rather than field names.
   - **Resolution**: Fixed by removing brittle private-field cache assertions and keeping observable source-service cache behavior coverage.

19. **[Tests]** `[Intentional - Flawed]`: `ConfigurationCacheIntegrationTest.setup()` defines the default fixture twice.
   - **Pragmatic Context**: Fixture duplication creates an unnecessary second source of truth for future test edits.
   - **Rationale**: The setup first writes an inline build script, then immediately calls `resetDefaultProjectFiles()` to rewrite `settings.gradle.kts`, `gradle.properties`, and `build.gradle.kts`.
   - **References**: `src/integrationTest/kotlin/dev/rnett/gradle/mcp/ConfigurationCacheIntegrationTest.kt:72`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/ConfigurationCacheIntegrationTest.kt:99`
   - **Recommendation**: Keep the default fixture definition in one helper and call it from setup.
   - **Resolution**: Fixed by removing duplicate default fixture setup and keeping `resetDefaultProjectFiles()` as the single source of truth in `ConfigurationCacheIntegrationTest`.

20. **[Documentation]** `[Mistake]`: `reports/review-1.md` overstates current documentation/spec completeness.
   - **Pragmatic Context**: This file is the active resolution-tracking artifact and can mislead future review passes.
   - **Rationale**: It marks documentation gaps as resolved even though generated docs still miss the inspect empty-scope note, graph-wide/no-child-closure wording, and runtime-512 clarity.
   - **References**: `reports/review-1.md:31`, `reports/review-1.md:35`, `reports/review-1.md:97`, `reports/review-1.md:101`, `reports/review-1.md:128`, `reports/review-1.md:132`
   - **Recommendation**: Update the report after fixing the docs/spec gaps, or mark those resolutions incomplete until they are actually reflected in generated artifacts.
   - **Resolution**: Fixed by updating `reports/review-1.md` to note that review-2 completed the remaining doc/spec gaps after generated docs and OpenSpec artifacts were updated.

## Open Questions for the Author
None.

## Positive Observations
- The core behavior has moved substantially closer to a single explicit contract: blank filters normalize to absent, regexes are runtime validated, and filter state is intended to stay in the view-cache layer rather than CAS.
- The implementation now has broader unit, service, integration, generated-doc, and OpenSpec coverage than earlier review passes.
- No reviewer found evidence that the regex currently leaks into CAS hashes, CAS directories, extraction markers, normalized paths, or indexes.

## Review Status
- **Pass**: 2
- **Open questions remaining**: 0
- **Next step**: Optional final review pass if desired; all findings in this report have been addressed and verified.
