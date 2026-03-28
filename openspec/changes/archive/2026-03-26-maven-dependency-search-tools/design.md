## Context

The `search_maven_central` tool in `DependencySearchTools.kt` has two modes:

1. **Keyword search**: `GET https://search.maven.org/solrsearch/select?q=<query>&rows=N&start=N&wt=json`. Returns packages matching the query. Investigation confirmed that packages published through the new Maven Central Portal (
   `central.sonatype.com`) — such as `ai.koog:koog-agents` — are entirely absent from this Solr index. No alternative public keyword search API exists: `central.sonatype.com` only exposes internal endpoints (
   `/api/internal/browse/components`), `mvnrepository.com` has no REST API, and `deps.dev`'s search (`/_/search`) is an undocumented internal BFF with no stability guarantee.

2. **Version listing**: `MavenRepoService.getVersions` reads `{repo}/{group}/{artifact}/maven-metadata.xml`. Returns a plain version list with no per-version dates. Also misses new Central packages (same coverage gap). Returns all versions
   with no default limit.

The `deps.dev` public REST API (`GET https://api.deps.dev/v3/systems/maven/packages/{group}%3A{artifact}`) covers all Maven packages including new Central, returns all versions with `publishedAt` timestamps (ISO 8601), and has a stability
and deprecation policy under v3.

## Goals / Non-Goals

**Goals:**

- Remove the keyword search mode (no viable public replacement).
- Switch version listing to `deps.dev` for full coverage and per-version publish dates.
- Apply a default limit of 5 most-recent versions with `PaginationInput` / `paginate()`.
- Update skills that reference keyword search.

**Non-Goals:**

- Using any undocumented/internal API.
- Covering private Maven repositories (out of scope for a Maven Central tool).

## Decisions

### D1: Remove keyword search mode

**Decision**: Remove the `query` parameter path in `DependencySearchTools` that calls `mavenCentralService.searchCentral`. The tool becomes version-lookup-only.

**Why**: Keyword search silently returns empty results for any package published through the new Central Portal. No public API fixes this. Exposing a broken search misleads users into thinking a package doesn't exist when it does.

**Alternative considered**: Keeping legacy Solr search with documented limitation. Rejected — the failure mode (silent empty results) is too subtle and the fraction of affected packages is growing as the new portal becomes the default
publishing path.

### D2: Switch version listing to deps.dev

**Decision**: Replace `MavenRepoService.getVersions` with `GET https://api.deps.dev/v3/systems/maven/packages/{group}%3A{artifact}`. Parse `versions[*].versionKey.version` and `versions[*].publishedAt`. Sort by `publishedAt` descending (
most-recent first). Format date as `yyyy-MM-dd`.

**Why**: Full package coverage, per-version publish dates, stable public API. The `maven-metadata.xml` approach has neither dates nor new Central coverage.

### D3: Default limit 5 with PaginationInput

**Decision**: Replace the existing `offset`/`limit` int parameters with `PaginationInput`. Default limit is **5**. Use the project's `paginate()` extension to slice the list and append the standard `Pagination: Showing X to Y of Z` footer.

**Why**: 5 is a sensible default for an initial version lookup — enough to see recent releases without overwhelming output. `PaginationInput` is the project-standard approach used by all other paginated tools.

### D4: Update skills

**Decision**: Update `skills/managing_gradle_dependencies/SKILL.md` and `skills/gradle_expert/SKILL.md` to remove references to using `search_maven_central` for keyword discovery. Update the tool description in `DependencySearchTools.kt` to
reflect the new version-lookup-only behavior.

## Risks / Trade-offs

- **[D1] Loss of keyword search**: Users can no longer search by keyword through this tool. They must know the `group:artifact` coordinates to use version lookup. This is a regression for package discovery but the feature was unreliable.
- **[D2] deps.dev lag**: `deps.dev` may lag slightly on very recently-published versions. Acceptable for a version listing tool.

## Migration Plan

Tool description and skills are updated to reflect the new behavior. No data model changes. The `paginate()` footer format is a non-breaking addition to the output.
