---
name: gradle-test
description: Running and investigating Gradle tests using consolidated tools and test-specific diagnosis workflows.
license: Apache-2.0
allowed-tools: gradlew inspect_build
metadata:
  author: rnett
  version: "2.7"
---

# Running and Investigating Tests

Instructions and examples for running tests with Gradle and analyzing test failures using consolidated tools.

## Directives

- **Use `gradlew`**: Run tests by passing the appropriate command line arguments (e.g., `["test"]`).
- **Use test filters**: Always filter tests using the `--tests` flag in `commandLine` to save time and resources (e.g., `["test", "--tests", "MyTestClass*"]`).
- **Identify task and project**: Ensure you specify the correct task path (e.g., `[":app:test"]`).
- **Check for build failures**: If a test run fails with a general error, use `inspect_build` with `failures: {}` and `problems: {}` to check if it's a compilation or configuration error.
- **Investigate specifically**: Use `inspect_build` with `tests: {}` to get structured output and stack traces for failed tests. For detailed investigation workflows, see [Test Diagnostics](references/test-diagnostics.md).

## When to Use

- When you need to run specific tests or all tests in a project.
- When you need to investigate why a test is failing.
- When you need to check the status of tests in a large project.

## Workflows

### Running Specific Tests

1. Identify the project path (e.g., `:app`) and the test task name (usually `test`).
2. Use `gradlew` with `commandLine` including `--tests` (e.g., `["test", "--tests", "com.example.MyTestClass*"]`).
3. If the tool reports failures, review the included console output.

### Investigating Test Failures

1. Identify the `BuildId` from the result.
2. If the build itself failed (not just tests), use `inspect_build(buildId=ID, failures={})`.
3. Use `inspect_build(buildId=ID, tests={outcome: "FAILED"})` to list all failed tests.
4. Use `inspect_build(buildId=ID, mode="details", tests={name: TNAME})` to see the full output and stack trace for a specific test.
5. For more advanced diagnostics, refer to [Test Diagnostics](references/test-diagnostics.md).

## Examples

### Run a single test class

```json
{
  "commandLine": [":module-a:test", "--tests", "com.example.service.MyServiceTest"]
}
```

### List all failed tests in a build

```json
{
  "buildId": "build_20240301_130000_def456",
  "tests": {
    "outcome": "FAILED"
  }
}
```

### Look up details for a specific failed test

```json
{
  "buildId": "build_20240301_130000_def456",
  "mode": "details",
  "tests": {
    "name": "com.example.a.MyTest.shouldFail"
  }
}
```

### Paginate through a large number of tests

```json
{
  "buildId": "build_20240301_130000_def456",
  "limit": 50,
  "offset": 100,
  "tests": {
    "name": "com.example.service"
  }
}
```

### Monitor a background test run until a specific test finishes

```json
{
  "buildId": "build_20240301_130000_def456",
  "wait": 60,
  "waitFor": "com.example.a.MyTest > shouldFail FAILED",
  "tests": {
    "mode": "details",
    "name": "com.example.a.MyTest.shouldFail"
  }
}
```

## Troubleshooting

- **No Tests Found**: Ensure your test pattern is correct. Patterns are prefixes of the fully qualified name. Try `*ClassName` if you're unsure of the package.
- **Results Truncated**: If there are many tests, use `inspect_build` with pagination (`offset` and `limit`).
- **Task Not Found**: In some projects (like Android), the test task might not be `test`. Use `inspect_build(tasks={})` or `gradlew(commandLine=["tasks"])` to find the correct task name.
