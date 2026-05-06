# Faceted Review Report
**Resolution finished**: [x]

## Executive Summary
This final review pass found no critical issues, but it did find multiple major correctness, contract, cache, and test gaps that should be addressed before treating the dependency regex filtering changes as final. The most important issues are around KMP source-set handling, `inspect_dependencies` empty-scope UX, filter traversal semantics, bounded view-cache state, matcher/init-script parity, and stale OpenSpec/report artifacts.

The latest intended contract remains: dependency regex filters are view-cache state, not CAS state; empty scoped dependency sets with a filter should succeed with a visible note; populated scopes with zero regex matches should error; and arbitrary Kotlin/JVM regexes are trusted input.

This report's documentation/spec resolutions were partially superseded by `reports/review-2.md`, which found that the generated-tool wording and OpenSpec text had not yet fully caught up to the intended contract. The follow-up source-artifact fixes for those gaps are tracked here explicitly below.

## Critical
No critical findings.

## Major
1. **[Logic]** `[Mistake]`: Kotlin source-set scopes can be treated as empty even when they have dependencies.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:359`
   - **Rationale**: `kotlinSourceSetConfigurationNamesFor` records Kotlin source-set declaration configurations such as `implementationConfigurationName`, but later filtering keeps only resolvable configurations. Kotlin source-set dependency buckets are typically not resolvable, so a KMP source set like `commonMain` can produce an empty scoped configuration list and return the empty-scope path instead of resolving dependencies.
   - **Recommendation**: Map Kotlin source sets to the resolvable compilation classpath configurations that actually contain their dependencies, or include resolvable configurations derived from the Kotlin compilations. Add an integration test for filtered `read_dependency_sources` on a KMP source set.
   - **Resolution**: Resolved by mapping Kotlin source sets to derived resolvable classpath configurations, preserving Kotlin source-set aliases through service post-filtering, and adding KMP source-set dependency-source integration coverage.

2. **[Logic]** `[Mistake]`: Default update checks can skip direct KMP platform artifacts because directness is compared against requested coordinates, not selected coordinates.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:877`
   - **Rationale**: `directDependencies` stores declared requested `group:name` values, while update candidates use selected `ModuleComponentIdentifier`s. For a direct KMP dependency declared as `kotlinx-coroutines-core`, Gradle may select/report `kotlinx-coroutines-core-jvm`; with `onlyDirect=true`, the selected module key is not in `directDependencies`, so it is excluded from update checking even though the rendered dependency is direct.
   - **Recommendation**: Derive direct update candidates from selected root dependency nodes, or match selected platform/common coordinates via `commonComponentId`/coordinate candidates instead of only declared `group:name`.
   - **Resolution**: Resolved by collecting selected root module coordinates into the direct-dependency set and adding a direct selected-coordinate update-check integration test.

3. **[Interface]** `[Mistake]`: `inspect_dependencies` drops the required empty-filtered-scope note.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:512`, `docs/tools/PROJECT_DEPENDENCY_TOOLS.md:14`, `openspec/specs/dependency-filtering/spec.md:37`
   - **Rationale**: When `dependencyFilterRegex != null` and `filterableDependencyCount == 0`, the init script falls through without a structured diagnostic. The formatter can return generic empty output such as `No dependency updates found.`, so users cannot distinguish a genuinely empty filtered scope from an ordinary no-dependency/no-update report. The docs/spec also do not clearly define inspect-specific empty-scope output.
   - **Recommendation**: Emit and parse a structured note when a dependency filter was supplied and the inspect scope has no dependency candidates; render it in normal and `updatesOnly` inspect output; document it in generated tool docs and OpenSpec.
   - **Resolution**: Resolved in implementation, then completed in the follow-up docs/spec pass by documenting the inspect empty-scope note behavior in tool-source text and OpenSpec before regenerating docs.

4. **[Build]** `[Mistake]`: Dependency filtering currently behaves like a graph-wide search, while the tool contract says transitives are shown for matched components.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:646`, `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/GradleDependencyTools.kt:31`
   - **Rationale**: A non-matching node still recurses into children, so a filter can emit a transitive dependency even when its direct parent did not match. That contradicts docs saying transitive children are shown for matched components when `onlyDirect=false`.
   - **Recommendation**: Decide the intended contract. If root-match semantics are intended, carry a `matchedAncestor` flag or stop descending from unmatched depth-1 nodes. If graph-wide search is intended, update the tool docs/specs to say matched transitive dependencies may be emitted independently.
   - **Resolution**: Resolved in implementation, then completed in the follow-up docs/spec pass by documenting the intended graph-wide matching contract: when `onlyDirect=false`, matching transitive nodes may appear independently, but matching a node does not implicitly include its children.

5. **[Tests]** `[Mistake]`: The configuration-cache reuse test does not assert cache reuse.
   - **Location**: `src/integrationTest/kotlin/dev/rnett/gradle/mcp/ConfigurationCacheIntegrationTest.kt:204`
   - **Rationale**: The test calls `read_dependency_sources` twice but only asserts that the second call succeeds and contains `slf4j/`. A regression where Gradle stores a fresh configuration-cache entry instead of reusing the first would still pass.
   - **Recommendation**: Assert evidence from the second Gradle invocation that configuration cache was reused, such as captured console output or build metadata with Gradle's reuse message.
   - **Resolution**: Resolved by asserting Gradle console evidence for configuration-cache reuse. The dependency-source reuse test restarts the MCP fixture between calls so it proves Gradle cache reuse instead of a service view-cache hit.

6. **[Tests]** `[Intentional - Flawed]`: Filtered update-check tests do not prove unrelated modules are excluded before update resolution.
   - **Location**: Filtered update-check integration coverage in the dependency/configuration-cache test suites.
   - **Rationale**: The tests assert `slf4j-api` is absent from final rendered output, but rendering is separately filtered. They would still pass if update resolution processed filtered-out dependencies and hid them later.
   - **Recommendation**: Make the filtered-out dependency observably fail/change behavior if included in update-check candidates, or expose raw update-check candidate metadata and assert only the matching coordinate is checked.
   - **Resolution**: Resolved by sharing `ModuleDependencyInfo.matchesRequestedScope` across latest-version resolution, update-check candidates, and source artifact selection, plus integration coverage for filtered and skipped update checks.

7. **[Docs]** `[Mistake]`: Stale review-report artifacts contradict the final contract.
   - **Location**: `reports/review_report_2026-05-04_dependency_regex_followup.md:49`
   - **Rationale**: The older report says empty filtered source scopes should fail, but the user-corrected contract is that genuinely empty scoped dependency sets with a filter succeed and surface a note.
   - **Recommendation**: Remove stale review reports from the changeset or rewrite them as explicitly superseded/current-state notes before committing.
   - **Resolution**: Resolved by deleting the stale `reports/review_report_*.md` artifacts and keeping only this active resolution report.

8. **[Docs/Architecture]** `[Mistake]`: Active OpenSpec change artifacts still frame the feature as source-tool-only even though `inspect_dependencies` is in scope.
   - **Location**: `openspec/changes/dependency-source-coordinate-regex-filtering/proposal.md:21`, `openspec/changes/dependency-source-coordinate-regex-filtering/proposal.md:25`, `openspec/changes/dependency-source-coordinate-regex-filtering/design.md:11`
   - **Rationale**: The permanent spec and generated docs apply regex filtering to `inspect_dependencies`, including update-check behavior. The active change metadata still names only dependency-source search/tool docs, which will mislead future archive/review work.
   - **Recommendation**: Update proposal, design, tasks, and delta specs to include `inspect_dependencies`, update-check filtering, `onlyDirect`/transitive implications, and schema/doc expectations.
   - **Resolution**: Resolved by updating the OpenSpec proposal, design, task list, and delta spec to include `inspect_dependencies`, update filtering, empty-scope notes, blank filters, and graph-wide matching semantics.

9. **[Docs]** `[Mistake]`: CAS path examples contradict the implemented versioned/sharded cache layout.
   - **Location**: `openspec/specs/cas-dependency-cache/spec.md:14`, `openspec/specs/cas-dependency-cache/spec.md:105`
   - **Rationale**: The spec says `cache/cas/v3/<hash>/` and later `.cache/cas/<hash>`, but current storage resolves CAS as `cacheDir/cas/v3/<hash-prefix>/<hash>`.
   - **Recommendation**: Normalize both doc locations to the actual versioned, sharded layout, for example `cache/cas/v3/<first-two-hash-chars>/<hash>/`.
   - **Resolution**: Resolved by normalizing CAS examples to `cache/cas/v3/<first-two-hash-chars>/<hash>/`.

10. **[Performance]** `[Mistake]`: Disabled/parallel providers eagerly and repeatedly traverse dependency graphs.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:444`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:445`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:852`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:906`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:921`
   - **Rationale**: `generate` always reads `latestVersions` and `sourcesFiles`; those providers call `scopedDependencyInfo.get()` before checking `checkUpdatesBool`/`downloadSourcesBool`. Update calls also re-query the same scoped dependency info. Source-only calls pay update-info traversal, and update calls repeat candidate collection/filtering.
   - **Recommendation**: Guard `latestVersions.get()` and `sourcesFiles.get()` by feature flags in `generate`, move `scopedDependencyInfo.get()` inside enabled branches, and share one selected-candidates provider/lazy value between update-related providers.
   - **Resolution**: Resolved by gating `latestVersions.get()` and `sourcesFiles.get()` on the enabled feature flags and moving scoped dependency-info realization inside enabled provider branches.

11. **[Performance]** `[Mistake]`: Unfiltered calls still allocate coordinate candidates for every component, variant, and rendered node.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:286`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:288`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:643`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:671`
   - **Rationale**: Candidate strings/lists/sets are built even when no dependency filter exists. Common unfiltered inspect/source paths now do extra O(components + variants + rendered nodes) work.
   - **Recommendation**: Only collect/store coordinate candidates when a dependency filter exists, and move renderer coordinate construction inside the filtered branch.
   - **Resolution**: Resolved by building coordinate candidates only when a dependency filter exists and moving renderer candidate construction inside filtered branches.

12. **[Security/Performance/Architecture]** `[Intentional - Flawed]`: Filtered session-view cache keys have unbounded in-memory cardinality.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:113`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:123`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourceStorageService.kt:345`
   - **Rationale**: The raw regex is part of `CacheKey`, and both `cache` and `keyLocks` are unbounded maps. Disk pruning removes old view directories, but in-memory `CachedView`, `depToCasDir`, and `Mutex` entries for every unique filter can live for the server lifetime.
   - **Recommendation**: Add bounded/TTL in-memory eviction for session views and locks, or avoid caching filtered views while keeping CAS stable. Remove corresponding `keyLocks` on eviction.
   - **Resolution**: Resolved with a Caffeine-backed 128-entry/30-minute in-memory session-view cache and bounded striped session-view locks, preserving the CAS boundary without custom per-filter lock-map eviction.

13. **[Security/Spec]** `[Intentional - Flawed]`: Coordinate length caps are applied after allocation and are not documented diagnostically.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:44`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:63`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:85`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:107`, `openspec/specs/dependency-filtering/spec.md:5`
   - **Rationale**: The 2048-character candidate cap prevents regex evaluation only after full coordinate strings are built and sometimes stored. It can also silently produce false negatives while the spec promises trusted Kotlin/JVM regex semantics and does not define coordinate-cap diagnostics.
   - **Recommendation**: Enforce coordinate length before allocating/storing full candidate strings. Also either document the coordinate cap and emit a diagnostic when skipped, or remove the cap if all otherwise-valid coordinates must be matchable.
   - **Resolution**: Resolved by removing the silent coordinate-length guard so valid coordinates are not skipped; later simplified further by removing dependency-filter length validation entirely.

14. **[Architecture/Clean Code]** `[Intentional - Flawed]`: Duplicated regex coordinate semantics no longer have a real parity guard.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:21`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:62`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcherTest.kt:24`
   - **Rationale**: Regex validation, preview sanitization, coordinate construction, coordinate length caps, variant handling, and unresolved fallback are duplicated in server Kotlin and the init script. The old source-text guard was deleted, and current unit tests exercise only the server matcher. Integration tests cover selected scenarios but do not protect the full mirrored contract from drift.
   - **Recommendation**: Generate the init-script helper from shared source, or add a data-driven parity test/corpus that exercises both implementations for resolved/unresolved candidates, variants, wrong variants, invalid regex, preview sanitization, blank inputs, and coordinate length caps.
   - **Resolution**: Resolved with behavior coverage across service matcher unit tests and init-script-backed integration tests for resolved, variant, unresolved, blank, no-match, empty-scope, source-set, and update-check cases.

15. **[Architecture/Interface]** `[Intentional - Flawed]`: MCP schema max-length logic is patched in generic `McpContext` using description text and a magic number.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpContext.kt:190`, `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpContext.kt:198`, `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpContext.kt:201`
   - **Rationale**: `withDependencyFilterMaxLength()` scans an English description phrase and hardcodes `512`. Changing docs can silently drop schema enforcement, and the schema limit can drift from runtime validation.
   - **Recommendation**: Superseded; there is no dependency-filter length constraint to expose in schema.
   - **Resolution**: Superseded by removing dependency-filter max-length validation and schema patching entirely; docs/specs now describe plain Kotlin/JVM regex semantics with blank filters treated as absent.

16. **[Architecture]** `[Mistake]`: Blank regex input is validated but dropped before Gradle invocation for `inspect_dependencies`.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt:322`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt:345`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:213`
   - **Rationale**: The previous validation path treated `""` as a valid Kotlin regex, but `addProp()` only forwarded non-blank strings. For `inspect_dependencies`, an empty regex was silently treated as no filter by Gradle, while source tools still applied the service-side matcher.
   - **Recommendation**: Reject blank dependency filters consistently at input validation, or forward blank filters intentionally and document full-string regex semantics for blank input.
   - **Resolution**: Resolved by normalizing blank filters to `null` at service/tool boundaries and documenting that blank filters are ignored.

## Minor
17. **[Docs/Interface]** `[Mistake]`: “Exact variant” examples are not exact variant filters.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceTools.kt:98`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:22`
   - **Rationale**: Examples labeled “exact variant” use `:.*runtime.*$`, which matches any variant containing `runtime`.
   - **Recommendation**: Relabel them as runtime-variant pattern examples, or use a concrete exact variant literal.
   - **Resolution**: Resolved by adding concrete exact-variant examples and relabeling wildcard runtime examples as runtime-variant patterns.

18. **[Docs]** `[Mistake]`: Generated tool examples omit group-only filtering despite tasks claiming group/artifact/version/variant examples are complete.
   - **Location**: `openspec/changes/dependency-source-coordinate-regex-filtering/tasks.md:18`, `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:18`
   - **Rationale**: Version and variant examples exist, but there is no agent-facing group-only example even though the permanent spec requires group filtering.
   - **Recommendation**: Add group-only examples to read/search dependency-source docs and an inspect example, or adjust completed task wording.
   - **Resolution**: Resolved by adding group-filter examples to source-tool docs and `inspect_dependencies` docs, then regenerating generated tool docs.

19. **[Docs]** `[Mistake]`: “Single dependency” / “Direct Dependencies Only” labels are stale for regex filters that can match groups, families, and transitives.
   - **Location**: `openspec/specs/dependency-filtering/spec.md:3`, `openspec/specs/dependency-filtering/spec.md:90`
   - **Rationale**: The scenarios support group and prefix/family matches and tests cover targeting transitives. The headings imply a narrower contract.
   - **Recommendation**: Rename to “Coordinate regex filtering” and “No implicit transitive closure”; clarify whether matched dependencies may be direct or transitive and whether matched nodes include their own children.
   - **Resolution**: Resolved in implementation, then completed in the follow-up docs/spec pass by renaming the spec requirements to coordinate regex filtering and graph-wide matching without implicit transitive closure, and by defining the `project::path` coordinate shape for project dependencies.

20. **[Docs]** `[Mistake]`: Generated tool docs under-document the trusted-regex performance contract.
   - **Location**: `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md:12`, `docs/tools/PROJECT_DEPENDENCY_TOOLS.md:14`, `openspec/changes/dependency-source-coordinate-regex-filtering/design.md:82`
   - **Rationale**: OpenSpec says arbitrary Kotlin regex performance is the caller’s responsibility, but generated docs only say “trusted input.”
   - **Recommendation**: Add an explicit sentence: “Filters run with Kotlin/JVM regex semantics; complex patterns may be expensive, and callers are responsible for trusted/safe regexes.”
   - **Resolution**: Resolved in implementation, then completed in the follow-up docs/spec pass by documenting that Kotlin/JVM regex filters are trusted input and complex patterns may be expensive.

21. **[Docs]** `[Mistake]`: Change-spec regex scenarios use JSON-escaped text without saying they are JSON strings.
   - **Location**: `openspec/changes/dependency-source-coordinate-regex-filtering/specs/dependency-source-search/spec.md:14`
   - **Rationale**: The scenario says “filter regex is” but uses `\\\\.` while the permanent spec uses actual regex spelling.
   - **Recommendation**: Change plain regex scenarios from `\\\\.` to `\\.`, or label them as JSON string examples.
   - **Resolution**: Resolved by cleaning the change-spec regex examples to use plain regex escaping instead of JSON-escaped strings.

22. **[Clean Code]** `[Mistake]`: The update/source filtering predicate is repeated independently across providers.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:877`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:910`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:927`
   - **Rationale**: Latest-version resolution, `updateCheckCandidates`, and source artifact resolution all repeat the “matches dependency filter and direct-depth scope” predicate. If one changes without the others, inspect output can report update/source state for a different dependency set than the rendered report.
   - **Recommendation**: Extract a helper such as `ModuleDependencyInfo.matchesRequestedScope(regex, onlyDirect, directDependencies)` and reuse it in all three providers.
   - **Resolution**: Resolved by extracting and reusing `ModuleDependencyInfo.matchesRequestedScope` for update resolution, update-check reporting, and source artifact filtering.

23. **[Idiom]** `[Mistake]`: Ambiguous buildscript/source-set predicate hurts auditability.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:467`
   - **Rationale**: A mixed `&&`/`||` condition relies on precedence in a source-set/buildscript branch that has already been bug-prone.
   - **Recommendation**: Extract a named boolean such as `shouldOutputBuildscriptSourceSet`, or parenthesize clauses explicitly.
   - **Resolution**: Resolved by extracting the buildscript/source-set output decision into `shouldOutputBuildscriptSourceSet`.

24. **[Performance]** `[Mistake]`: The update version-selection callback reallocates stable markers and recompiles the version regex per candidate version.
   - **Location**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:861`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:867`
   - **Rationale**: The stable marker list and optional `Regex` are recreated in `componentSelection.all` for each candidate version.
   - **Recommendation**: Hoist the non-stable marker list and compile `versionRegex` once before registering the callback.
   - **Resolution**: Resolved by hoisting the stable-version marker list and optional version regex outside the component-selection callback.

25. **[Performance]** `[Mistake]`: `dependencyCoordinateCandidates` pays a redundant `distinct()` allocation for a deterministic list of at most two entries.
   - **Location**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:73`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:117`
   - **Rationale**: The call runs in dependency/variant hot paths and can be avoided.
   - **Recommendation**: Return the pre-sized list directly or match candidates inline without materializing the list.
   - **Resolution**: Resolved by removing the redundant `distinct()` allocation from dependency coordinate candidate helpers.

26. **[Tests/Idiom]** `[Mistake]`: Negative assertions use `assertTrue(!...)` despite `assertFalse` being available.
   - **Location**: Dependency-filter test assertions in the dependency/configuration-cache test suites.
   - **Rationale**: The file imports `assertFalse`, but uses `assertTrue(!...)`, making intent less direct and creating mixed assertion style.
   - **Recommendation**: Replace with `assertFalse(text.contains("..."), "...")`.
   - **Resolution**: Resolved by replacing the targeted negative assertions with `assertFalse`.

27. **[Tests/Idiom]** `[Mistake]`: Message substring checks should use semantic assertions.
   - **Location**: `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:110`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:111`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:148`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:149`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:167`
   - **Rationale**: `assertTrue(error.message.orEmpty().contains(...))` is less readable and gives weaker assertion intent than `assertContains`.
   - **Recommendation**: Import `kotlin.test.assertContains`, assign `val message = error.message.orEmpty()`, and use `assertContains(message, "...")`.
   - **Resolution**: Resolved by converting the targeted message substring checks to `assertContains` with local message variables.

## Open Questions
- Resolved: stale `reports/review_report_*.md` artifacts were removed from the changeset.

## Positive Observations
- Reviewers found no CAS identity leak from dependency regex into source hashes, CAS directories, lock paths, or session view UUIDs.
- Reviewers found no concrete configuration-cache task-action regression in the inspected code.
- Invalid regex/schema/source-service coverage appears to exist, though some tests need stronger assertions.

## Review Status
- **Pass**: Final-pass review after prior review rounds.
- **Open questions remaining**: 0
- **Next step**: Follow-up source docs/spec verification should regenerate tool docs and rerun the focused tool-metadata/schema checks before final review or merge preparation.
