## Context

The `gradle-mcp` test suite currently takes over a minute to execute. The primary bottlenecks are:

1. `GradleSourceServiceTest`: Downloads and extracts a full Gradle source distribution (~60MB), taking ~1 minute.
2. REPL Integration Tests: Starting REPL worker processes for each test class is slow (~20-30s per class).
3. Gradle Project setup: Creating fresh Gradle projects and resolving dependencies for each test adds significant overhead.

## Goals / Non-Goals

**Goals:**

- Reduce `GradleSourceServiceTest` duration from ~60s to < 5s.
- Reduce REPL integration test overhead by sharing environments or using a persistent cache.
- Improve overall test suite performance (target < 30s total).

**Non-Goals:**

- Removing integration tests entirely; we still want to verify real Gradle interaction.
- Mocking all Gradle interactions; some tests MUST run real Gradle.

## Decisions

### 1. Mocking Gradle Source Downloads

**Decision:** In `GradleSourceServiceTest`, mock the `HttpClient` to return a minimal, valid ZIP file instead of downloading from `services.gradle.org`.

- **Rationale:** The test verifies extraction, indexing, and filtering logic, not the download process itself. A minimal ZIP with the expected directory structure (e.g., `subprojects/core/src/main/kotlin/...`) is sufficient.
- **Alternatives:**
    - Use a pre-downloaded ZIP: Increases repository size and doesn't handle different Gradle versions well.
    - Skip the test: Compromises coverage of critical indexing logic.

### 2. Shared Gradle Working Directory for Tests

**Decision:** Use a stable, shared working directory for all integration tests instead of unique temp directories for each class.

- **Rationale:** A shared directory allows tests to reuse the Gradle dependency cache and indexed sources across different test classes, significantly reducing resolution time.
- **Alternatives:**
    - Unique temp dirs: Safe but extremely slow due to cold caches.

### 3. Lightweight REPL Integration Tests

**Decision:** Optimize the test projects used in REPL tests to have minimal dependencies and use the same Kotlin/Compose versions as the host project to ensure they are already in the local Gradle cache.

- **Rationale:** Heavy dependency resolution is a major part of the REPL startup time in tests.
- **Alternatives:**
    - Pre-built REPL worker: Complex to manage and might not match the specific test requirements.

### 4. Caching for `GradleProjectFixture`

**Decision:** Implement a caching mechanism for `testGradleProject` based on the hash of the project configuration.

- **Rationale:** Many tests use identical or very similar project structures.
- **Alternatives:**
    - Manual project reuse: Error-prone and hard to maintain.

## Risks / Trade-offs

- **[Risk]** Shared working directory might lead to test interference. → **Mitigation**: Use unique project names/paths within the shared working directory.
- **[Risk]** Mocking `HttpClient` might miss real-world download issues. → **Mitigation**: Keep a separate, less frequent E2E test that performs a real download, or rely on production usage for that specific part.
