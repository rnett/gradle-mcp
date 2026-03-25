# Capability: ephemeral-session-views

## Purpose

TBD

## ADDED Requirements

### Requirement: Ephemeral Session Views

The system SHALL create a unique, immutable snapshot directory for every dependency resolution tool call.

- The path MUST be uniquely identified by a timestamp and UUID.
- It SHALL contain a `manifest.json` describing the View's contents.
- It MUST contain a `sources/` subdirectory with symlinks or junctions to the global CAS cache.

#### Scenario: Tool call view isolation

- **WHEN** a resolution tool call starts
- **THEN** it creates its own private session view and returns its sources path

### Requirement: Stable Snapshot Mapping

A tool call SHALL maintain a consistent mapping of dependencies for its entire duration.

- It MUST load the manifest once into memory at the start.
- Any parallel updates to the project SHALL NOT affect the active tool's snapshot.

#### Scenario: View stability during update

- **WHEN** a second process updates the project manifest
- **THEN** the first process continues its search using its own session directory and manifest
