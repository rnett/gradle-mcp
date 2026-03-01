---
name: gradle-test
description: Running and investigating Gradle tests using specialized tools and test-specific diagnosis workflows.
license: Apache-2.0
allowed-tools: run_tests_with_gradle run_many_test_tasks_with_gradle lookup_build_tests lookup_latest_builds lookup_build_failures
metadata:
  author: rnett
  version: "1.1"
---

# Running and Investigating Tests

Instructions and examples for running tests with Gradle and analyzing test failures.

## Directives

- **Prefer specialized tools**: Use `run_tests_with_gradle` or `run_many_test_tasks_with_gradle` instead of running tests via `run_gradle_command ["test"]`.
- **Use test filters**: Always filter tests using the `tests` parameter (class or package name prefixes, with `*` wildcards) to save time and resources.
- **Identify task and project**: Ensure you specify the correct `projectPath` (e.g., `:lib`) and `taskName` (e.g., `test`, `testDebugUnitTest`).
- **Check for build failures**: If a test run fails with a general error, use `lookup_build_failures` to check if it's a compilation or configuration error rather than a test failure. For detailed investigation workflows, see the
  `gradle-build` skill.
- **Publish scans for complex failures**: If you can't diagnosis a failure from the console, run with `scan: true` for a Develocity Build Scan.
- **Investigate specifically**: Use `lookup_build_tests` to get structured output and stack traces for failed tests.

## When to Use

- When you need to run specific tests or all tests in a project.
- When you need to investigate why a test is failing.
- When you need to check the status of tests in a large project.

## Workflows

### Running Specific Tests

1. Identify the project path (e.g., `:app`) and the test task name (usually `test`).
2. Use `run_tests_with_gradle` with a `tests` array (e.g., `["com.example.MyTestClass*"]`).
3. If the tool reports failures, review the included console output.

### Running Multiple Test Tasks

1. If you need to run tests in multiple subprojects or multiple test tasks, use `run_many_test_tasks_with_gradle`.
2. Provide a map of task paths to test patterns.

### Investigating Test Failures

1. Identify the `BuildId` from the result.
2. If the build itself failed (not just tests), use `lookup_build_failures(buildId=ID, summary={})`. For deep analysis of build errors, refer to the `gradle-build` skill.
3. Use `lookup_build_tests(buildId=ID, summary={outcome="FAILED"})` to list all failed tests.
4. Use `lookup_build_tests(buildId=ID, details={testName=TNAME})` to see the full output and stack trace for a specific test.
5. If you still can't find the root cause, re-run the test with `scan: true` and use a Develocity MCP server.

## Examples

### Run a single test class

```json
{
  "projectPath": ":module-a",
  "taskName": "test",
  "tests": [
    "com.example.service.MyServiceTest"
  ]
}
```

### Look up details for a failed test

```json
{
  "buildId": "build_20240301_130000_def456",
  "details": {
    "testName": "com.example.a.MyTest.shouldFail"
  }
}
```

## Troubleshooting

- **No Tests Found**: Ensure your test pattern is correct. Patterns are prefixes of the fully qualified name. Try `*ClassName` if you're unsure of the package.
- **Results Truncated**: If there are many tests, use `lookup_build_tests` with pagination (`offset` and `limit`).
- **Task Not Found**: In some projects (like Android), the test task might not be `test`. Use `lookup_build_tasks` or `run_gradle_command ["tasks"]` to find the correct task name.
