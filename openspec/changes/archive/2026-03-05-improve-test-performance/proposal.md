## Why

The current test suite takes over a minute to run, with a few specific tests accounting for the majority of the duration. Slow feedback loops hinder development productivity and can lead to developers skipping tests. Improving the
performance of these bottleneck tests is essential for maintaining a fast and reliable CI/CD pipeline and local development experience.

## What Changes

- **Optimization of Gradle Source Tests**: Refactor `GradleSourceServiceTest` to avoid expensive network calls and full source downloads by using mocked or pre-downloaded local sources where possible.
- **REPL Test Speedup**: Improve the efficiency of REPL integration tests (`ComposeReplIntegrationTest`, `KmpReplIntegrationTest`, `JavaReplIntegrationTest`) by sharing REPL environments across tests or optimizing the test projects they
  use.
- **Concurrent Test Execution Improvements**: Investigate and address overhead in concurrent test execution as seen in `ConcurrentSameProjectTest` and `GradleProviderTest`.
- **Test Infrastructure Refinement**: General cleanup and optimization of test resources (Gradle projects, GradleProvider) which are currently expensive to create.

## Capabilities

### New Capabilities

- `gradle-source-test-optimization`: Optimizes tests that interact with Gradle source downloads to be fast and hermetic.
- `repl-test-performance`: Provides a faster testing strategy for REPL integration tests without sacrificing coverage.
- `test-environment-optimization`: Reduces the overhead of setting up and tearing down expensive test environments (Gradle projects, REPLs).

### Modified Capabilities

- None

## Impact

- **Test Suite Duration**: Significant reduction in total test execution time (target < 30s).
- **Development Workflow**: Faster feedback for developers.
- **CI/CD**: Reduced resource usage and faster build times.
- **Codebase**: Changes to test infrastructure and potentially how REPLs are managed in tests.
