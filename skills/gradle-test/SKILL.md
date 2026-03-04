---
name: gradle-test
description: Execute and diagnose tests at scale with intelligent filtering and specialized workflows for rapid failure resolution. Use to run specific tests or investigate test failures.
license: Apache-2.0
allowed-tools: gradlew inspect_build
metadata:
  author: rnett
  version: "2.8"
---

# Advanced Test Execution & Diagnostic Workflows

Run tests efficiently with precision filtering and leverage deep diagnostic tools to isolate and fix failures fast within the Gradle environment.

## Directives

- **ONLY use MCP tools**: NEVER use `./gradlew` via a shell unless you have exhausted all attempts to use the `gradlew` tool and it repeatedly fails to meet your requirements. Falling back to the shell is a **last resort** for edge cases
  where the Tooling API or the server's output capturing is demonstrably insufficient.
- **Use test filters**: Always filter tests using the `--tests` flag in the `commandLine` of `gradlew` to save time and resources (e.g., `["test", "--tests", "MyTestClass*"]`).
- **Identify task and project**: Ensure you specify the correct task path in `gradlew` (e.g., `[":app:test"]`).
    - `:task` (starts with colon): Targets the task in the **root project only**.
    - `task` (no leading colon): Targets the task in **all projects** (root and all subprojects).
    - `:app:task`: Targets the task in the `app` subproject.
- **Background for long test suites**: ALWAYS set `background: true` in `gradlew` for test suites that take a long time to run. This allows you to continue working while monitoring the test progress.
- **Monitor with `inspect_build`**: Use the `inspect_build` tool to check the status of background test runs or to get detailed information about any build.
- **Provide absolute `projectRoot` when in doubt**: Provide `projectRoot` as an **absolute file system path** to any Gradle MCP tool that supports it unless you are certain it is not required. **Relative paths are not supported.**
- **Check for build failures**: If a test run fails with a general error, use the `inspect_build` tool with `failures: {}` and `problems: {}` to check if it's a compilation or configuration error.
- **Investigate specifically**: Use the `inspect_build` tool with `tests: {}` to get structured output and stack traces for failed tests. For detailed investigation workflows, see [Test Diagnostics](references/test-diagnostics.md).

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
