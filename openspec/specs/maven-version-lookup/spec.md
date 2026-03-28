# Spec: Maven Version Lookup

## Purpose

Defines requirements for the `lookup_maven_versions` tool, which retrieves available versions for a Maven package using the `deps.dev` public REST API.

---

## Requirements

### Requirement: Keyword Search Mode Removed

The `lookup_maven_versions` tool SHALL NOT support keyword search. No public API covers the full Maven package index for keyword search — packages published via the new Central Portal are absent from the legacy Solr, and no alternative
public search API exists.

### Requirement: Version List Uses deps.dev with Publish Dates and Default Limit

The `lookup_maven_versions` tool SHALL retrieve version data from the `deps.dev` public REST API, returning versions sorted most-recent first with per-version publish dates, and applying a default limit of 5.

#### Scenario: Default limit applied when not specified

- **WHEN** `lookup_maven_versions` is called with no `limit` in pagination
- **THEN** the response SHALL include at most 5 versions, ordered most-recent first
- **THEN** each version entry SHALL include the publish date formatted as `yyyy-MM-dd`
- **THEN** the response SHALL include a pagination footer (e.g., `Pagination: Showing 1 to 5 of 28`)

#### Scenario: Explicit limit overrides the default

- **WHEN** `lookup_maven_versions` is called with `pagination.limit=10`
- **THEN** the response SHALL include at most 10 versions

#### Scenario: Pagination works with offset

- **WHEN** `lookup_maven_versions` is called with `pagination.limit=10` and `pagination.offset=10`
- **THEN** the response SHALL include versions 11–20 in most-recent-first order

#### Scenario: New Central packages are found

- **WHEN** `lookup_maven_versions` is called for a package published via the new Central Portal (e.g., `coordinates=ai.koog:koog-agents`)
- **THEN** the response SHALL return versions (deps.dev covers all Maven packages including new Central)
