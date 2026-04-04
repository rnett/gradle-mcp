## 1. Infrastructure Refactor

- [x] 1.1 Update `SourceStorageService.kt` to include a versioned CAS root (e.g., `cas/v2`).
- [x] 1.2 Update `SourceStorageService.getCASDependencySourcesDir` to use the new versioned root.
- [x] 1.3 Update `CASDependencySourcesDir` in `SourcesDir.kt` to calculate `advisoryLockFile` within the versioned root.
- [x] 1.4 Remove `.completed-v2` marker (and related logic) if it was solely intended for layout versioning, as the root path now handles this.

## 2. Verification and Cleanup

- [x] 2.1 Update any integration tests that manually construct CAS paths (e.g., `ViewMergingIntegrationTest.kt`).
- [x] 2.2 Verify that a fresh cache is correctly populated in `cas/v2/`.
- [x] 2.3 Verify that multiple server instances (simulated or real) do not contend for locks across versions.
- [x] 2.4 Run `./gradlew test integrationTest` to ensure no regressions.
