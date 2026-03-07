## 1. Locking Infrastructure

- [x] 1.1 Create `FileLockManager` or a utility class for managing `FileChannel` locks with deterministic timeouts.
- [x] 1.2 Implement a robust `tryLockWithTimeout` method that logs progress and retries until the timeout is reached.
- [x] 1.3 Ensure the locking utility works reliably on Windows (handling potential access exceptions during lock acquisition).

## 2. Refactor SourcesService

- [x] 2.1 Implement project-level locking in `DefaultSourcesService` for all `download*` methods using a `.lock` file in the metadata directory.
- [x] 2.2 Refactor `extractSources` to use granular, dependency-specific locks in the `globalSourcesDir`.
- [x] 2.3 Implement robust `try-finally` blocks to ensure all locks are released, even if extraction or indexing fails.

## 3. Refactor GradleSourceService

- [x] 3.1 Implement version-level locking in `DefaultGradleSourceService` to prevent concurrent downloads and extractions of the same Gradle version.
- [x] 3.2 Add detailed logging for the Gradle source extraction and indexing lifecycle.

## 4. Refactor IndexService

- [x] 4.1 Implement dependency-level locking in `DefaultIndexService.index` to prevent concurrent indexing of the same dependency.
- [x] 4.2 Implement locking in `mergeIndices` to prevent multiple threads from merging into the same project index simultaneously.

## 5. Verification and Diagnostics

- [x] 5.1 Create an integration test that simulates concurrent source requests to verify that only one process extracts while others wait or fail gracefully.
- [x] 5.2 Verify that a manually held lock triggers the 60-second timeout and returns a clear error message instead of hanging.
- [x] 5.3 Audit diagnostic logs to ensure they provide enough information to identify the source of any future contention issues.
