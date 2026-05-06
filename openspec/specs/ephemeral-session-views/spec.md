# Capability: ephemeral-session-views

## Purpose

Defines immutable per-request dependency-source session views and the cache semantics that allow safe reuse without mutating active views.

## Requirements

### Requirement: Ephemeral Session Views

The system SHALL create or reuse an immutable snapshot directory for dependency resolution requests. Newly materialized session-view directories SHALL be unique; later requests MAY reuse a cached view when the same scope/filter cache key is still valid.

- The path MUST be uniquely identified by a timestamp and UUID.
- It SHALL contain a `manifest.json` describing the View's contents.
- It MUST contain a `sources/` subdirectory with symlinks or junctions to the global CAS cache.

#### Scenario: Tool call view isolation

- **WHEN** a resolution tool call starts
- **THEN** it receives an immutable session view and returns its sources path

#### Scenario: Reusing a cached session view

- **WHEN** the same scope and dependency filter are requested again before cache expiry
- **THEN** the system MAY return the existing immutable session-view directory instead of materializing a new one
- **AND** the reused view SHALL remain stable for the full duration of the later tool call

### Requirement: Stable Snapshot Mapping

A tool call SHALL maintain a consistent mapping of dependencies for its entire duration.

- It MUST load the manifest once into memory at the start.
- Any parallel updates to the project SHALL NOT affect the active tool's snapshot.

#### Scenario: View stability during update

- **WHEN** a second process updates the project manifest
- **THEN** the first process continues its search using its own session directory and manifest
