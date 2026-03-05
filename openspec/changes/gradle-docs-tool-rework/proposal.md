# Proposal: Rework Gradle Docs Tool with Distribution Download and Lucene Indexing

## Summary

Rework the `gradle_docs` MCP tool to download Gradle distribution archives from
`https://services.gradle.org/distributions/` and use a unified Apache Lucene index for
fast full-text search across all documentation types (User Guide, DSL, Javadoc,
Samples, and Release Notes), replacing the current slow HTTP-scraping approach.

## Problem

The current `gradle_docs` tool has several significant limitations:

1. **Slow search**: `searchDocs` fetches every page in the User Guide one by one over HTTP,
   making a full-guide search take dozens of seconds or longer.
2. **Userguide only**: Only the User Guide is accessible. Javadocs, DSL references, and
   samples are unavailable despite being essential for API-level and example-driven queries.
3. **Online-only**: Every page read requires a network request. No offline access after
   initial use.
4. **Fragile scraping**: The page-list parsing depends on the navigation HTML structure,
   which can change across Gradle versions.

## Proposed Solution

- **Download the `-docs` distribution** (`gradle-{version}-docs.zip`) on first use for a
  given version. This archive (~89 MB) contains `docs/userguide/`, `docs/dsl/`,
  `docs/kotlin-dsl/`, `docs/javadoc/`, `docs/samples/`, and `docs/release-notes.html`
  in their entirety — no binaries or source code.
- **Extract content by kind** into a local cache directory. For `samples/`, also unpack
  each nested sample ZIP and index its README and build files as a single document.
- **Build a unified Lucene index** per version after extraction. Every document in the
  index is tagged with its `kind` (e.g., `userguide`, `javadoc`).
- **Add a `kind` parameter** to the tool: `userguide`, `dsl`, `kotlin-dsl`, `javadoc`,
  `release-notes`, `samples`, and a special `all` scope to search everything at once.
- **Sub-second search**: Subsequent searches query the local index directly — no HTTP
  required.

## Benefits

| Concern       | Before                        | After                                                       |
|---------------|-------------------------------|-------------------------------------------------------------|
| Search speed  | Fetches all pages serially    | Sub-second Lucene query                                     |
| Content types | Userguide only                | Userguide, DSL, Kotlin DSL, Javadoc, Samples, Release Notes |
| OmniSearch    | Not possible                  | Search "all" kinds in a single query                        |
| Offline use   | Always online                 | Offline after first download                                |
| Reliability   | Depends on nav HTML structure | Reads files directly from ZIP                               |

## Trade-offs

- **First-use cost**: The `-docs` distribution is ~89 MB. Download takes a few seconds
  on a fast connection but may be slow on limited bandwidth.
- **Disk space**: Each cached version uses ~200–400 MB (extracted content + Lucene index).
  A `version` argument is required to avoid downloading multiple versions unnecessarily.
- **New dependencies**: Adds `lucene-core`, `lucene-analysis-common`, `lucene-queryparser`
  to the project's dependency set.

## Non-Goals

- This proposal does not change how version detection from `projectRoot` works.
- This proposal does not add new MCP tools — only modifies the existing `gradle_docs` tool.
- The unified index does not replace the markdown conversion cache (for reading pages).
