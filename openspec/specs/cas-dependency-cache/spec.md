# Capability: cas-dependency-cache

## Purpose

TBD

## ADDED Requirements

### Requirement: Global CAS Dependency Cache

The system SHALL store extracted sources and indices in a global, immutable cache identified by the content hash of the dependency.

- Extraction and indexing MUST be performed in an isolated temporary directory.
- The finalized directory MUST be moved atomically into its final CAS location.
- If the CAS location already exists **and is complete** (has completion marker), the temporary directory MUST be discarded.

### Requirement: Self-Repairing Cache

The system SHALL automatically detect and repair broken CAS entries during processing.

- **IF** a CAS directory exists but lacks the `.base-completed` marker
- **THEN** the system SHALL clear the directory and attempt a fresh extraction/normalization.
- Finalization MUST use an atomic replace operation to ensure consistency even if a broken directory existed.

#### Scenario: Repairing interrupted extraction

- **WHEN** a previous process was killed while extracting sources
- **THEN** a subsequent process detects the missing marker, clears the partial data, and completes a fresh extraction.

#### Scenario: First-time processing

- **WHEN** a dependency is not in the CAS cache
- **THEN** the system extracts, indexes, and moves it to the hash-based path

#### Scenario: Concurrent processing

- **WHEN** two parallel processes calculate the same hash
- **THEN** both attempt to process it, and only one successfully completes the atomic move while the other is discarded

### Requirement: Advisory Lock Optimization

The system SHALL use a non-blocking advisory lock to coordinate processing of the same hash.

- A process MUST attempt to acquire a `tryLock()` on an advisory file before starting expensive extraction or indexing.
- If the lock is denied, the process SHALL suspend and poll for the final CAS directory instead of performing redundant work.

#### Scenario: Optimized parallel extraction

- **WHEN** one process holds the advisory lock for a specific hash
- **THEN** a second process for the same hash waits (polls) for the first one to finish
