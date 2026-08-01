## ADDED Requirements

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
