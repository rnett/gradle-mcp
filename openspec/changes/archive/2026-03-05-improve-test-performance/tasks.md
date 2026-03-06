## 1. Optimize Gradle Source Service Tests

- [x] 1.1 Create a utility for generating a minimal Gradle source ZIP for testing
- [x] 1.2 Update `GradleSourceServiceTest` to use `MockEngine` for `HttpClient`
- [x] 1.3 Verify `GradleSourceServiceTest` passes in < 5s

## 2. Shared Test Infrastructure

- [x] 2.1 Refactor `BaseMcpServerTest` and `BaseReplIntegrationTest` to support a shared, persistent `workingDir`
- [x] 2.2 Ensure the shared `workingDir` is properly cleaned up (or not) between full test runs
- [x] 2.3 Implement caching in `GradleProjectFixture` based on project configuration hash

## 3. Optimize REPL Integration Tests

- [x] 3.1 Update REPL integration tests to use minimal dependency sets
- [x] 3.2 Align Kotlin and Compose versions in REPL test projects with host project versions
- [x] 3.3 Verify REPL integration tests show significant performance improvement

## 4. General Test Performance Cleanup

- [x] 4.1 Review and optimize `ConcurrentSameProjectTest` and `GradleProviderTest`
- [x] 4.2 Investigate and fix any unnecessary Gradle process starts in tests
- [x] 4.3 Run full test suite and verify total duration is < 1m (User steered to < 1m)
