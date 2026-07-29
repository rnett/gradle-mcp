# Capability: gradle-best-practices

## Purpose

Defines the structure, coverage, and behavior of the generated best-practices reference content produced by the `generate-best-practices-doc` capability. Output is a `best-practices/` directory with individual `.md` files per best-practice subsection plus a categorized `_index.md` index for LLM agent consumption.

## ADDED Requirements

### Requirement: Coverage of official Gradle best practices

The generated reference SHALL cover all best-practices pages present in the Gradle docs distribution for the pinned version. This includes, at minimum: DSL style and project structure, build performance and configuration cache, dependency management, task configuration and lazy APIs, project integrity and reproducibility, and testing best practices.

#### Scenario: Comprehensive coverage

- `WHEN` an agent reads the `best-practices/_index.md`
- `THEN` the index SHALL list entries covering every best-practices page in the docs distribution
- `AND` each entry SHALL link to the corresponding detail file and be grouped under its source page (area) heading.

### Requirement: Per-subsection directory structure

The generated reference SHALL be written into a `best-practices/` directory containing:
- Individual `.md` files, one per best-practice subsection (for large pages with >3 `##` sections).
- Whole-page files for small pages (≤3 `##` sections), stored directly in the directory.
- A categorized `_index.md` file as the entry point.

Large pages are split at each `##` heading boundary; heading levels are promoted by one (`##` → `#`, `###` → `##`). The `### Tags` section is extracted for the index and removed from generated files. Small pages remain unsplit.

#### Scenario: Directory output

- `WHEN` generation completes
- `THEN` the `best-practices/` directory SHALL contain multiple `.md` files including `_index.md`
- `AND` no single monolithic `best_practices.md` file SHALL exist.

### Requirement: Categorized discoverable index

The `_index.md` SHALL provide structured discoverability for agents:
- Entries grouped under headings named after the source page (area).
- Each entry links to the detail file with a one-line auto-derived summary (first paragraph, links stripped, 160-char truncation) and backtick-wrapped tags.
- An inverted "Browse by Tag" cross-reference section at the bottom listing all tags alphabetically with associated slugs.

#### Scenario: Agent navigation through index

- `WHEN` an agent loads the `_index.md`
- `THEN` the document SHALL start with a description line directing the agent to read it first and pick by area or tag
- `AND` SHALL group all entries under their source-page headings with summaries and tags
- `AND` SHALL include a "Browse by Tag" section at the bottom.

### Requirement: Freshness disclaimer

The generated reference SHALL include a disclaimer that it was generated from a specific Gradle version on a specific date, and that the most up-to-date guidance is available via the `gradle_docs` tool with `tag:best-practices`.

#### Scenario: Version transparency

- `WHEN` an agent reads the `_index.md`
- `THEN` the document SHALL state the Gradle version it was generated from
- `AND` SHALL direct the agent to `gradle_docs tag:best-practices` for authoritative, version-appropriate guidance.
