# Faceted Review Report
**Resolution finished**: [x]

## Executive Summary
Brief merge-focused review of the current uncommitted changes found no direct correctness regressions in the dependency-regex, init-script, source-view cache, JDK-source, or structured-output paths. The remaining issues are mostly merge-integration maintainability and test-contract gaps around the newly combined `main` behavior. The highest-value follow-up is to tighten the `dependency = "jdk"` selector path so it does not trigger broad dependency source acquisition, then add focused coverage for the JDK selector and duplicated regex candidate semantics.

## Major
1. **[Architecture]** `[Intentional - Flawed]`: `dependency = "jdk"` still triggers broad project dependency source resolution before returning a JDK-only view.
   - **Evidence**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:173`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:183`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:350`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:1224`
   - **Rationale**: The service strips the `jdk` selector before delegating to `GradleDependencyService`, so project-scope JDK-only source reads call the init script with `dependency = null` and `downloadSources = true`. The resulting Gradle work can resolve/download source artifacts for the whole scoped graph even though the final session view is intended to include only JDK sources. This preserves output correctness but weakens the intended layering and performance boundary: `jdk` should be a session-view selector, not a cause for unrelated module-source acquisition.
   - **Recommendation**: Short-circuit JDK-only source reads before dependency-source download, or pass enough intent into the Gradle side to avoid source artifact work when the selected view is JDK-only.
   - **Resolution**: Fixed in `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt` by routing `dependency = "jdk"` through a normalized selector and resolving Gradle metadata with `downloadSources = false` before building the JDK-only view.

2. **[Maintainability]** `[Intentional - Flawed]`: Dependency-filter/JDK policy is duplicated across `DefaultSourcesService` entrypoints and the shared resolver.
   - **Evidence**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:173`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:197`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:230`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:321`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:350`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:357`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:373`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:382`
   - **Rationale**: The public entrypoints normalize raw strings, strip the special `jdk` selector, and build matchers, while `resolveAndProcessSourcesInternal` re-derives `jdkOnly`, `includeJdk`, and error behavior from the same raw value. Future filter or selector changes have to stay synchronized across multiple paths.
   - **Recommendation**: Introduce one small normalized selector value object or helper result at the service boundary, then pass that through the shared resolver.
   - **Resolution**: Fixed in `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt` by introducing a single `SourceDependencySelector` boundary helper passed into the shared resolver.

3. **[Maintainability]** `[Intentional - Flawed]`: Init-script source-set scoping is split across parallel helper stacks.
   - **Evidence**: `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:308`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:323`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:376`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:404`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:462`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:479`
   - **Rationale**: `sourceSetConfigurationNames` and `collectTargetSourceSetConfigurationNames` both parse `kotlin:` source-set names and decide how to derive configuration names. Additional helpers then encode Java and Kotlin source-set heuristics, and `generate()` applies another filter. This is a merge-sensitive area where future scope-rule changes could be applied to only one path.
   - **Recommendation**: Consolidate source-set metadata derivation into one authoritative representation that both configuration-time metadata emission and execution-time filtering consume.
   - **Resolution**: Addressed in `src/main/resources/init-scripts/dependencies-report.init.gradle.kts` by centralizing target source-set parsing in `targetSourceSetSelection` and reusing it from metadata and execution-time helpers.

4. **[Tests]** `[Mistake]`: The duplicated regex candidate semantics no longer have a direct Kotlin-vs-init-script parity guard.
   - **Evidence**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:36`, `src/main/resources/init-scripts/dependencies-report.init.gradle.kts:691`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcherTest.kt:30`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt:341`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt:393`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt:455`
   - **Rationale**: The Kotlin matcher and init-script renderer both implement canonical coordinate candidate logic. Existing tests cover the matcher in isolation and a few end-to-end paths, but no test proves both implementations stay aligned across resolved, unresolved, variant, and fallback candidate shapes.
   - **Recommendation**: Add a small behavior/parity test that exercises the actual init-script path for the same coordinate matrix covered by `DependencyFilterMatcherTest`, or otherwise generate/share the candidate helper.
   - **Resolution**: Addressed in `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt` with init-script-backed regex matrix coverage for group-only, group:name, exact version, variant fallback, wrong variant, unresolved G:A, and no-match behavior.

5. **[Tests]** `[Mistake]`: The exact-version variant integration test does not prove exact-version or variant behavior.
   - **Evidence**: `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt:341`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt:355`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt:356`
   - **Rationale**: The test named around exact version matching only asserts that a serialization dependency appears and `slf4j` does not. A regression that broadens filtering to `group:name` could still pass.
   - **Recommendation**: Assert the selected dependency/version text that proves the exact `group:name:version` regex matched a variant-bearing dependency through the intended fallback.
   - **Resolution**: Fixed in `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/search/GradleDependencyIntegrationTest.kt` by asserting the selected serialization version and JVM variant presence, plus negative wrong-version and wrong-variant failures.

6. **[Tests]** `[Mistake]`: The explicit `dependency = "jdk"` selector path is not covered.
   - **Evidence**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:350`, `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesService.kt:385`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:103`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:144`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:175`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:200`
   - **Rationale**: Existing tests cover JVM metadata auto-inclusion/skipping, but not the explicit selector. A regression where `dependency = "jdk"` includes regular dependency sources, uses the wrong cache key, or throws normal dependency-filter errors would not be caught.
   - **Recommendation**: Add a focused unit or integration test for explicit `dependency = "jdk"` showing JDK-only output, cache-key behavior, and no normal zero-match/no-sources errors.
   - **Resolution**: Fixed in `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt` with an explicit `dependency = "jdk"` test proving JDK-only output and no normal dependency source download/hash work.

## Minor
7. **[Docs/Skills]** `[Mistake]`: Stale skill guidance still shows old dependency-filter shorthand.
   - **Evidence**: `src/main/skills/managing_gradle_dependencies/SKILL.md:68`, `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/GradleDependencyTools.kt:32`, `src/main/kotlin/dev/rnett/gradle/mcp/tools/dependencies/GradleDependencyTools.kt:57`
   - **Rationale**: The skill example still uses `inspect_dependencies(dependency="org.mongodb:mongodb-driver-sync")`, but `dependency` is now a full-string regex over `group:name:version[:variant]`. Agents following the skill may get zero matches for normal resolved modules.
   - **Recommendation**: Update the skill example to a regex such as `^org\\.mongodb:mongodb-driver-sync(:.*)?$` or an exact version example.
   - **Resolution**: Fixed in `src/main/skills/managing_gradle_dependencies/SKILL.md` by updating targeted examples to full-string regex syntax.

8. **[Tests]** `[Intentional - Flawed]`: One cache-key regression test combines too many behaviors.
   - **Evidence**: `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:396`, `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/DefaultSourcesServiceDependencyFilterTest.kt:47`
   - **Rationale**: Filtered-key separation, cache reuse, forced refresh invalidation, blank-filter normalization, and CAS identity are all asserted in one long scenario. Failures will be harder to diagnose than in the newly added focused dependency-filter tests.
   - **Recommendation**: Split the scenario into focused tests for cache-key separation, exact-key invalidation, blank-filter normalization, and CAS identity.
   - **Resolution**: Fixed in `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt` by splitting the overloaded scenario into focused cache-key, force-refresh, blank-filter, and CAS-identity tests.

9. **[Tests]** `[Intentional - Flawed]`: The KMP view-merging integration test still reads like a probe rather than a specification.
   - **Evidence**: `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/ViewMergingIntegrationTest.kt:95`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/ViewMergingIntegrationTest.kt:100`, `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/ViewMergingIntegrationTest.kt:105`
   - **Rationale**: The test gathers common/platform search results but only asserts that each returned path exists. It would remain green if both queries collapsed onto the same variant or if duplicate/common shadowing regressed.
   - **Recommendation**: Assert distinct expected package/source roots or dependency prefixes for the common and platform queries.
   - **Resolution**: Fixed in `src/integrationTest/kotlin/dev/rnett/gradle/mcp/dependencies/ViewMergingIntegrationTest.kt` by asserting common and JVM source roots are both present and that common/platform searches do not collapse to the same paths.

10. **[Tests]** `[Mistake]`: New tests use bare `assert(...)` instead of explicit Kotlin test assertions.
   - **Evidence**: `src/test/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceToolsTest.kt:149`, `src/test/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceToolsTest.kt:189`
   - **Rationale**: Bare JVM assertions provide weaker failure reporting and depend on assertion enablement. The surrounding file and project style use `kotlin.test` assertions.
   - **Recommendation**: Replace with `assertTrue`, `assertFalse`, or `assertContains` as appropriate.
   - **Resolution**: Fixed in `src/test/kotlin/dev/rnett/gradle/mcp/tools/dependencies/DependencySourceToolsTest.kt` by replacing bare assertions with `kotlin.test.assertTrue`.

11. **[Idioms]** `[Mistake]`: `DependencyFilterMatcher` is public even though it is an implementation helper.
   - **Evidence**: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt:8`
   - **Rationale**: The matcher is used internally by the dependencies package plus tests, and the nearby helper functions are `internal`. Keeping the type public unnecessarily widens the API surface.
   - **Recommendation**: Make the class `internal` unless external consumers intentionally need it.
   - **Resolution**: Fixed in `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/DependencyFilterMatcher.kt` by making `DependencyFilterMatcher` internal.

12. **[Tests]** `[Mistake]`: A repeated comment looks like merge residue.
   - **Evidence**: `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt:367`
   - **Rationale**: The same comment appears multiple times in sequence, reducing readability in a merge-sensitive test.
   - **Recommendation**: Keep one comment or remove it if the test is self-explanatory.
   - **Resolution**: Fixed in `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/SourcesServiceCachingTest.kt` by removing duplicate repeated comments.

## Open Questions
- None.

## Positive Observations
- The logic review found no concrete correctness regressions in the dependency regex filtering path, init-script render/scoping logic, service-side source filtering/cache-key behavior, JDK selector handling, or structured-output parsing.
- The session-view cache key vs CAS identity split appears preserved: dependency regex filters affect view identity but not CAS hashes/directories.
- Full verification was reported as already passing before this review, including `:test integrationTest` and `check`.

## Review Status
- **Pass**: 3
- **Open questions remaining**: 0
- **Next step**: Address the `jdk` selector layering/performance issue first, then tighten the targeted test gaps and small cleanup items.
