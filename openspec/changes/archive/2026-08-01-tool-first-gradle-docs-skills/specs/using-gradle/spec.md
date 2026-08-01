## MODIFIED Requirements

### Requirement: Authoritative Documentation Routing
The skill MUST route agents from every major operating topic to the authoritative version-scoped Gradle documentation exclusively through the `gradle_docs` tool, using only verified tag and path hints and never fabricated tool names. The skill MUST NOT embed published `docs.gradle.org` URLs or `gradle-mcp.rnett.dev` tool-documentation pointers as documentation citations; the `gradle_docs` hint is the single routing mechanism.

#### Scenario: Research a major Gradle topic
- **WHEN** an agent needs authoritative guidance on execution, testing, troubleshooting, dependencies, compatibility, caching, task selection, wrapper integrity, or deprecation behavior
- **THEN** the relevant local reference provides a version-scoped `gradle_docs` tag and path hint (resolved to the project's Gradle version) and routes exclusively through `gradle_docs`, with no published `docs.gradle.org` URL and no `gradle-mcp.rnett.dev` pointer
- **AND** the agent reads `gradle/wrapper/gradle-wrapper.properties` before applying version-sensitive advice

#### Scenario: Override the documentation version
- **WHEN** an agent researches a Gradle version that differs from the project's wrapper (for example a migration target or a specific minor release being verified for a bug fix)
- **THEN** the research guidance directs the agent to pass an explicit `version="X.Y"` to `gradle_docs`, and otherwise to omit `version` so it resolves to the detected wrapper
- **AND** the guidance warns that a coarse version such as `"8"` fails and that silently using the latest release when the wrapper is older is incorrect

#### Scenario: Escalate a documentation lookup
- **WHEN** an agent's first `gradle_docs` search is too narrow or returns nothing
- **THEN** the research guidance provides a lookup ladder — scoped `tag:<tag> <term>` search, then broaden by dropping the tag, then browse the tree with `path="."`, then read a specific `path` — and notes that the no-argument call lists available sections

#### Scenario: Follow cross-topic references
- **WHEN** an agent moves between execution, testing, troubleshooting, dependency inspection, and research
- **THEN** the references preserve local cross-links and the authoritative `gradle_docs` tag and path hints remain available at the topic's procedure home
