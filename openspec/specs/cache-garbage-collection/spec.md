# Capability: cache-garbage-collection

## Purpose

TBD

## ADDED Requirements

### Requirement: Session View Pruning

The system SHALL periodically prune old session view directories based on creation time or last access.

- It MUST attempt a non-recursive delete of directories older than 24 hours.
- If the directory is held open by an OS process (e.g., Windows), the deletion SHALL be skipped and retried on the next pass.

#### Scenario: Automated session cleanup

- **WHEN** a session view directory is 24 hours old
- **THEN** it is deleted from the metadata store

### Requirement: CAS Cache Mark-and-Sweep

The system SHALL implement a mark-and-sweep garbage collector for the global CAS cache.

- It MUST collect all hashes referenced in any active `manifest.json` files.
- Any hash in the CAS cache not found in the reference set SHALL be deleted if it has not been accessed for a defined grace period (e.g., 7 days).

#### Scenario: Orphaned CAS deletion

- **WHEN** a CAS hash is no longer referenced by any session manifest and the grace period has passed
- **THEN** it is pruned from the global cache
