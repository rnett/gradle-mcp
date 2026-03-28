## Why

The `search_maven_central` tool has two problems: (1) keyword search silently returns no results for packages published via the new Maven Central Portal (`central.sonatype.com`) — these packages are absent from the legacy Solr index and no
public keyword search API covers the full index; (2) version listing uses `maven-metadata.xml` which has no per-version publish dates and also misses new Central packages, and dumps all versions by default with no limit.

## What Changes

- **Remove keyword search**: The keyword search mode is removed. No public API covers the full Maven package index for keyword search — `central.sonatype.com` and `deps.dev` only expose internal/undocumented search endpoints. Continuing to
  surface it would mislead users with silent empty results.
- **Switch version listing to deps.dev**: Replace `MavenRepoService.getVersions` (reads `maven-metadata.xml`) with the `deps.dev` public REST API (`GET https://api.deps.dev/v3/systems/maven/packages/{group}%3A{artifact}`). This covers all
  packages (including new Central), returns per-version publish dates, and is a stable, documented public API.
- **Default limit of 5 with standard pagination**: Apply a default limit of 5 most-recent versions using `PaginationInput` + the project's `paginate()` utility.

## Capabilities

### New Capabilities

<!-- None -->

### Modified Capabilities

- `search-maven-central`: Keyword search mode removed. Version listing now uses deps.dev, returns publish dates, and applies a default limit with pagination.

## Impact

- `src/main/kotlin/.../tools/dependencies/DependencySearchTools.kt`: remove keyword search path; add deps.dev HTTP client call; replace offset/limit int params with `PaginationInput`; add date formatting.
- `skills/managing_gradle_dependencies/SKILL.md` and `skills/gradle_expert/SKILL.md`: remove references to keyword search usage of `search_maven_central`.
- `./gradlew :updateToolsList` required after tool description changes.
