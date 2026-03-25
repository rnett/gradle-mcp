# Capability: source-processing-granular-progress

## Purpose

TBD

## ADDED Requirements

### Requirement: Stable Parallel Progress Reporting

The system SHALL report indexing progress in a stable manner that avoids flickering between different dependencies. It MUST maintain high-frequency progress updates, but the dependency name mentioned in the progress message MUST remain
stable (e.g., by "focusing" on one active dependency at a time) rather than updating to whichever dependency emitted the most recent log line.

#### Scenario: Multiple dependencies indexing in parallel

- **WHEN** multiple dependencies are being indexed simultaneously
- **THEN** the progress message SHALL report the name of one currently processing dependency
- **AND** the dependency name in the message SHALL NOT flicker rapidly, but instead remain stable until that specific dependency finishes processing
- **AND** the progress updates SHALL continue to be emitted at their original high frequency
