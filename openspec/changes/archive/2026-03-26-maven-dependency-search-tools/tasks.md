## 1. Remove Keyword Search Mode

- [x] 1.1 In `DependencySearchTools.kt`, remove the `query` parameter path that calls `mavenCentralService.searchCentral` — the tool becomes version-lookup-only
- [x] 1.2 Update the tool description to reflect the removed keyword search and the new version listing behavior

## 2. Switch Version Listing to deps.dev

- [x] 2.1 In `DependencySearchTools.kt`, replace `MavenRepoService.getVersions` with a call to `GET https://api.deps.dev/v3/systems/maven/packages/{group}%3A{artifact}`
- [x] 2.2 Parse `versions[*].versionKey.version` and `versions[*].publishedAt`; sort by `publishedAt` descending (most-recent first); format date as `yyyy-MM-dd`
- [x] 2.3 Replace the existing `offset`/`limit` int parameters with `PaginationInput`; use the project's `paginate()` utility; default limit is **5**

## 3. Update Skills

- [x] 3.1 In `skills/managing_gradle_dependencies/SKILL.md`, remove the directives and workflow steps that use `search_maven_central` for keyword discovery; update Workflow 3 to reflect that the tool is for version lookup of known packages
  only
- [x] 3.2 In `skills/gradle_expert/SKILL.md`, update the "Adding a Dependency" workflow and example to remove keyword search usage

## 4. Tests & Verification

- [x] 4.1 Add unit tests for the deps.dev version listing (pagination, date formatting, new Central packages)
- [x] 4.2 Run `./gradlew :updateToolsList` after the tool description changes
- [x] 4.3 Run `./gradlew test` and confirm all tests pass
