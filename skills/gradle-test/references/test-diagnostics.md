# Test Diagnostics Reference

This document provides detailed instructions for diagnosing test failures using the `inspect_build` tool.

## Inspecting Test Results

When a build fails due to test failures, use `inspect_build` with the `tests` inclusion to get a structured view of the results.

### Listing Failed Tests

To see all failed tests in a build:

```json
{
  "buildId": "BUILD_ID",
  "tests": {
    "outcome": "FAILED"
  }
}
```

### Getting Detailed Test Output

To see the full output and stack trace for a specific test:

```json
{
  "buildId": "BUILD_ID",
  "mode": "details",
  "tests": {
    "name": "com.example.MyTest.testMethod"
  }
}
```

## Build Failures vs. Test Failures

Sometimes a test run fails not because a test failed, but because the build itself failed (e.g., compilation error, configuration error, or a task dependency failed).

If `inspect_build` shows no failed tests but the build status is `FAILED`, or if you suspect a non-test issue:

1. **Check Build Failures and Problems**:
   ```json
   {
     "buildId": "BUILD_ID",
     "failures": {},
     "problems": {}
   }
   ```
2. **Refer to General Build Failure Analysis**: For a deep dive into non-test related failures, see the [Failure Analysis](../../gradle-build/references/failure-analysis.md) guide in the `gradle-build` skill.

## Common Test Failure Scenarios

### Assertion Failures

Look for `AssertionError` in the test output. The `inspect_build` tool will provide the expected vs actual values if available.

### Timeouts

If a test times out, it may be marked as `ERROR` or `FAILED`. Check the console output for "Timeout" messages.

### Infrastructure Issues

If many tests fail with similar errors (e.g., `NoClassDefFoundError`, `DatabaseConnectionException`), check the `failures` and `problems` sections of the build inspection as shown above.

## Pagination and Filtering

For builds with a large number of tests, use `limit` and `offset`:

```json
{
  "buildId": "BUILD_ID",
  "limit": 50,
  "offset": 0,
  "tests": {
    "name": "com.example.service"
  }
}
```

## Functional Identity with Foreground Execution

Monitoring background test runs via `inspect_build` provides exactly the same rich diagnostic data as foreground execution. Both methods utilize progressive disclosure to provide concise summaries and structured results, ensuring session
history remains clean. Background execution is simply a non-blocking alternative that allows you to perform other tasks while the test suite proceeds.
