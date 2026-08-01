## MODIFIED Requirements

### Requirement: Materialization Validation
MUST implement `verifySkillsMaterialized` to gate the `check` task by verifying shared fan-out, generated content hashes, index completeness, and the documentation-routing invariant that no skill markdown file contains a URL whose host is `docs.gradle.org` or `gradle-mcp.rnett.dev`. The URL invariant SHALL be a host blocklist scoped to those two documentation-citation hosts; other external URLs (for example the skill `author:` metadata URL, Maven Central Portal guides, and example or license URLs inside code snippets) SHALL NOT be flagged.

#### Scenario: Detect manual drift
- **WHEN** a developer manually edits a materialized file in a skill root instead of the source
- **THEN** `verifySkillsMaterialized` detects the drift from the authoritative source and fails the build

#### Scenario: Reject a blocked documentation URL
- **WHEN** any skill markdown file (authored or generated) contains a URL whose host is `docs.gradle.org` or `gradle-mcp.rnett.dev`
- **THEN** `verifySkillsMaterialized` reports the file and URL as a violation and fails the build

#### Scenario: Permit non-documentation external URLs
- **WHEN** a skill markdown file contains an external URL whose host is not `docs.gradle.org` or `gradle-mcp.rnett.dev` (for example `central.sonatype.org` or the `author:` metadata URL)
- **THEN** `verifySkillsMaterialized` does not flag it
