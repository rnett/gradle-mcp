# Capability: gradle-docs-querying

## Purpose

Specifies how documentation tools use resolved concrete version strings for caching, indexing, and retrieval, and how best-practices content is searchable.
## Requirements
### Requirement: Resolved versions for documentation

The documentation querying tools SHALL use concrete version strings resolved from aliases for all caching, indexing, and content retrieval operations. The default cache root is `~/.mcps/rnett-gradle-mcp/cache/reading_gradle_docs` (
overridable via `GRADLE_MCP_WORKING_DIR`).

#### Scenario: Query documentation with "current"

- **WHEN** the user queries documentation for version `"current"`
- **THEN** the system SHALL resolve `"current"` to a concrete version (e.g., `"8.6.1"`) and use that version for the documentation cache directory (e.g., `…/reading_gradle_docs/8.6.1/`)

### Requirement: Transparent version feedback

The system SHALL inform the user when a version alias has been resolved to a concrete version in its tool output.

#### Scenario: Display resolved version in tool output

- **WHEN** the documentation tool resolves `"current"` to `"8.6.1"`
- **THEN** the output header SHALL indicate that Gradle `"8.6.1"` is being used (resolved from `"current"`)

### Requirement: Search by Best Practices Tag

The documentation search tool SHALL allow users to filter for specifically tagged "best-practices" documentation.

#### Scenario: Searching for best practices

- **WHEN** user provides `query="tag:best-practices performance"` to the documentation search tool
- **THEN** only documentation tagged with `best-practices` and containing "performance" SHOULD be returned.

### Requirement: Search by Upgrading Tag

The documentation search tool SHALL allow users to filter for specifically tagged "upgrading" documentation covering version migration and breaking changes.

#### Scenario: Searching for upgrading guides

- **WHEN** user provides `query="tag:upgrading map notation"` to the documentation search tool
- **THEN** only documentation tagged with `upgrading` and containing "map notation" SHOULD be returned.

### Requirement: Section Summary Integration

The documentation section summary SHALL explicitly list the `best-practices` and `upgrading` tags to aid in discoverability.

#### Scenario: Summarizing documentation sections

- **WHEN** the documentation tool is called with no arguments
- **THEN** the returned summary MUST include a "Best Practices" section with its corresponding count
- **AND** MUST include an "Upgrading Gradle" section with its corresponding count when upgrading pages are present.

### Requirement: Fragment and query handling for page reads

The `gradle_docs` tool SHALL accept a `path` that carries an optional `#fragment` and an optional `?query`, and SHALL resolve the base page after stripping them. A `?query` SHALL be ignored for page reads (with a note in the output). A `#fragment` SHALL resolve to the corresponding section of the converted page when the fragment can be matched; when it cannot be matched, the tool SHALL throw an error whose message names both the requested page path and the unresolved fragment. HTML anchor ids SHALL be preserved during HTML→Markdown conversion so that document-defined fragments (for example `#sec:exclude-trans-deps`) are resolvable.

#### Scenario: Read a page without a fragment
- **WHEN** `gradle_docs` is called with `path="userguide/dependency_resolution.md"`
- **THEN** the tool SHALL return the full converted markdown for that page, unchanged from prior behavior

#### Scenario: Normalize an HTML path that carries a fragment
- **WHEN** `gradle_docs` is called with `path="userguide/resolution_rules.html#sec:exclude-trans-deps"`
- **THEN** the tool SHALL strip the fragment, normalize the base `userguide/resolution_rules.html` to `userguide/resolution_rules.md`, and resolve the page instead of reporting "Docs page not found"

#### Scenario: Resolve a document-defined fragment to its section
- **WHEN** `gradle_docs` is called with a `path` whose fragment matches an anchor id preserved from the source HTML (for example `#sec:exclude-trans-deps` on a heading)
- **THEN** the tool SHALL return the section beginning at that heading and ending before the next heading of the same or higher level, prefixed with a short header naming the resolved fragment

#### Scenario: Resolve a heading-text slug fragment
- **WHEN** `gradle_docs` is called with a fragment that matches the slug of a heading but no element carries the literal id
- **THEN** the tool SHALL return that heading's section as a best-effort resolution

#### Scenario: Reject an unresolvable fragment
- **WHEN** `gradle_docs` is called with a fragment that matches neither a preserved id nor a heading slug
- **THEN** the tool SHALL throw an error whose message names both the requested page path and the unresolved fragment

#### Scenario: Ignore a query string on a page read
- **WHEN** `gradle_docs` is called with a `path` that includes a `?query` component
- **THEN** the tool SHALL strip the query string, resolve the base page, and append a note that the query string was ignored for the page read

### Requirement: Accurate version parameter documentation

The `gradle_docs` tool's `version` parameter description SHALL state the full resolution chain: an explicit `version` wins; otherwise the version is auto-detected from the project wrapper via `projectRoot`; otherwise it falls back to the latest stable Gradle release. The description SHALL NOT imply that auto-detection from the project is the terminal fallback.

#### Scenario: Description documents the latest-stable fallback
- **WHEN** an agent reads the `version` parameter description
- **THEN** the description SHALL mention that, absent an explicit version and a detectable project wrapper, the tool resolves to the latest stable Gradle version

#### Scenario: Tool metadata stays synchronized
- **WHEN** the `version` parameter description is changed
- **THEN** the generated tool documentation SHALL be regenerated so the published description matches the source

### Requirement: Latest stable version surfaced in the server instructions

The server instructions SHALL state the latest stable Gradle version resolved at server startup from `https://services.gradle.org/versions/current`. The statement SHALL be provenance-aware: it must not assert that a fallback value is the live latest version. The `gradle_docs` tool description SHALL NOT contain the version statement, the version-check endpoint, or any hard-coded Gradle version, so the tool description remains version-insensitive. The `gradle` tool description SHALL NOT carry the version statement either, because `gradle` executes the project's wrapper version rather than the latest stable release.

#### Scenario: Successful startup resolution
- **WHEN** the server resolves the latest stable version from `https://services.gradle.org/versions/current` at startup
- **THEN** the server instructions SHALL state the resolved latest stable Gradle version

#### Scenario: Failed startup resolution
- **WHEN** the startup resolution fails because `https://services.gradle.org/versions/current` is unreachable
- **THEN** the server instructions SHALL identify the Gradle version the server was built against as the resolved value
- **AND** SHALL identify that value as the server's bundled Gradle version
- **AND** SHALL NOT assert that the bundled version is the live latest

#### Scenario: Version statement absent from the gradle_docs tool description
- **WHEN** an agent reads the `gradle_docs` tool description
- **THEN** the description SHALL NOT contain the latest stable version statement
- **AND** SHALL NOT contain `https://services.gradle.org/versions/current`
- **AND** SHALL NOT contain a hard-coded Gradle version
- **BECAUSE** the resolved version is surfaced in the server instructions instead

#### Scenario: Version statement absent from the gradle tool description
- **WHEN** an agent reads the `gradle` tool description
- **THEN** the description SHALL NOT contain the latest stable version statement
- **BECAUSE** `gradle` runs the project's wrapper version, not the latest stable release

#### Scenario: Doc content links pinned to the resolved version
- **WHEN** a `gradle_docs` response contains documentation content or search snippets with `https://docs.gradle.org/current/` URLs
- **THEN** those URLs SHALL be rewritten to `https://docs.gradle.org/<resolved version>/`

#### Scenario: Generated docs stay in sync
- **WHEN** the server instructions or a tool description wording is changed
- **THEN** the generated tool documentation SHALL be regenerated (`:updateToolsList`) so the published description matches the source

