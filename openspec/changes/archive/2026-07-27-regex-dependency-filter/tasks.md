# Tasks: regex-dependency-filter

## Phase 1: Delta specs — already created in the change artifacts

The delta specs at `openspec/changes/regex-dependency-filter/specs/` are already written. At implementation time, run `openspec apply regex-dependency-filter` to merge them into the main specs.

- [x] **Task 1.1**: During implementation, run `openspec apply regex-dependency-filter` to sync delta specs into `openspec/specs/dependency-filtering/spec.md`
- [x] **Task 1.2**: Verify the merged spec at `openspec/specs/dependency-filtering/spec.md` contains the new "Server-side post-processing contract" requirement with all scenarios

## Phase 2: Test coverage — filterDependencyTree unit tests

The test approach: since `filterDependencyTree` is a private function on `DefaultGradleDependencyService`, write a dedicated test class (`FilterDependencyTreeTest`) that creates a `DependencyFilterMatcher` instance and calls the logic directly by extracting the tree-pruning logic into a package-private helper, or by exercising it through `DefaultGradleDependencyService.getDependencies()` with a mocked `GradleProvider`. The preferred approach is to extract `filterDependencyTree` to an `internal` top-level function in `DependencyFilterMatcher.kt` so it can be tested directly without mocking the Gradle build.

- [x] **Task 2.1**: Extract `filterDependencyTree` from `DefaultGradleDependencyService` into an `internal` top-level function in `DependencyFilterMatcher.kt`, keeping the service delegate to it.
- [x] **Task 2.2**: Create test file `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/FilterDependencyTreeTest.kt` covering:
  - Matching parent with non-matching children (children pruned)
  - Non-matching parent with matching child (parent preserved as structural node)
  - Both parent and child matching (both kept)
  - Neither parent nor child matching (both pruned)
  - Version filter interaction with tree pruning
  - Empty list input (returns empty)
  - No filter (all kept)
- [x] **Task 2.3**: Run `./gradlew test` to verify all existing + new tests pass

## Phase 3: Validation

- [x] **Task 3.1**: Run `./gradlew test` to confirm all tests pass
- [x] **Task 3.2**: Run `./gradlew :updateToolsList` (mandatory after any source changes, though no tool metadata changes expected)
- [x] **Task 3.3**: Verify the spec addition is consistent with the existing "Graph-Wide Matching Without Implicit Closure" requirement
