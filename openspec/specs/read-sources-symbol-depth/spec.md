# Spec: read-sources-symbol-depth

## Purpose

Defines the depth-aware rendering behavior for filesystem and package listings in the `read_dependency_sources` tool. Controls how directories and sub-packages are annotated when expansion is limited by depth, and how recursive expansion is
capped to avoid excessive index queries.

---

## Requirements

### Requirement: Filesystem listing annotates unexpanded directories with item count

When `walkDirectoryImpl` reaches a directory at the maximum depth and does not recurse into it, the directory entry in the output SHALL include a parenthetical count of its direct children (files and subdirectories combined), e.g.,
`collections/  (42 items)`.

#### Scenario: Directory at depth limit has children

- **WHEN** `walkDirectory` is called with `maxDepth=2` and a directory at depth 2 contains 5 entries
- **THEN** the output line for that directory SHALL end with `  (5 items)`

#### Scenario: Directory at depth limit is empty

- **WHEN** a directory at the depth limit has no children
- **THEN** the output line SHALL end with `  (0 items)`

#### Scenario: Directories within max depth are not annotated

- **WHEN** a directory is listed and its children WILL be walked (i.e., it is not at the depth limit)
- **THEN** no item count suffix SHALL appear on its output line

---

### Requirement: Package listing expands two sub-package levels by default

When a package path is resolved via the symbol index and `formatPackageContents` is called, the output SHALL expand sub-packages one additional level (depth 2 total) beyond the immediate sub-packages (depth 1).

#### Scenario: Top-level package with sub-packages

- **WHEN** a package has sub-packages `[collections, coroutines]` and each has direct symbols
- **THEN** the output SHALL show each sub-package with its direct symbols listed beneath it

#### Scenario: Sub-package at depth 2 has further sub-packages

- **WHEN** a depth-2 sub-package itself has sub-packages (depth 3+)
- **THEN** those deeper sub-packages SHALL NOT be expanded; instead their names SHALL appear with a symbol count annotation

---

### Requirement: Unexpanded sub-packages show symbol count

At the deepest listed level of a package listing, each unexpanded sub-package entry SHALL display the count of direct symbols it contains (e.g., `io/  (12 symbols)`). If the sub-package has no direct symbols but has further sub-packages of
its own, it SHALL display the sub-package count instead (e.g., `io/  (3 sub-packages)`).

#### Scenario: Unexpanded sub-package with direct symbols

- **WHEN** a sub-package at the deepest expanded level has 8 direct symbols
- **THEN** its listing entry SHALL show `  (8 symbols)`

#### Scenario: Unexpanded sub-package with only sub-packages and no direct symbols

- **WHEN** a sub-package at the deepest expanded level has 0 direct symbols and 3 sub-packages
- **THEN** its listing entry SHALL show `  (3 sub-packages)`

#### Scenario: Unexpanded sub-package that is empty

- **WHEN** a sub-package at the deepest expanded level has 0 symbols and 0 sub-packages
- **THEN** its listing entry SHALL show no count annotation

---

### Requirement: Recursive expansion is capped to avoid excessive index queries

If a package has more than 30 direct sub-packages, the listing SHALL NOT expand each sub-package individually. Instead it SHALL display sub-packages as a flat list with a note indicating expansion was skipped due to size.

#### Scenario: Package with many sub-packages skips recursive expansion

- **WHEN** a package has 31 or more direct sub-packages
- **THEN** the output SHALL list sub-package names without recursive expansion and SHALL include a note such as `(too many sub-packages to expand; use a more specific path)`
