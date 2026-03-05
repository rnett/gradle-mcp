## 1. Core Implementation

- [x] 1.1 Create `dev.rnett.gradle.mcp.gradle.dependencies.search.GlobSearch` implementing `SearchProvider`
- [x] 1.2 Implement indexing logic in `GlobSearch` to store all file paths in `filenames-v1.txt`
- [x] 1.3 Implement search logic in `GlobSearch` using `java.nio.file.FileSystem.getPathMatcher` with glob syntax
- [x] 1.4 Register `GlobSearch` in `DefaultIndexService`'s `providers` list
- [x] 1.5 Add `GLOB` to `SearchType` enum in `DependencySourceTools.kt`
- [x] 1.6 Implement snippet calculation logic that skips blank lines, package/import declarations, and comments (including multiline blocks)
- [x] 1.7 Ensure snippet calculation handles various file types (e.g., XML, Properties) by adjusting skipping rules as needed

## 2. Tool & UI Updates

- [x] 2.1 Update `searchDependencySources` tool to handle `SearchType.GLOB`
- [x] 2.2 Update `searchDependencySources` tool description and examples in `DependencySourceTools.kt`
- [x] 2.3 Update `docs/tools/PROJECT_DEPENDENCY_SOURCE_TOOLS.md` with the new GLOB search capability

## 3. Verification & Testing

- [x] 3.1 Create unit tests for `GlobSearch` to verify indexing and glob matching
- [x] 3.2 Add integration tests in `DependencySourceToolsTest.kt` for the new GLOB search type
- [x] 3.3 Verify snippet filtering logic (skipping boilerplate/comments) with test cases
- [x] 3.4 Run `./gradlew check` to ensure all tests pass and documentation is up to date
- [x] 3.5 Verify the change manually using a test project and `search_dependency_sources` tool
