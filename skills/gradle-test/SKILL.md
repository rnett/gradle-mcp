---
name: gradle-test
description: >
  Execute and diagnose tests at scale with high-precision filtering and authoritative failure isolation. 
  This skill is the STRONGLY PREFERRED way to manage your testing lifecycle, offering features like surgical test selection via '--tests' flags, 
  managed background execution for long-running suites, and deep diagnostic integration for rapid failure resolution. 
  Use it to run specific test classes or methods, monitor background test runs, or perform detailed post-mortem analysis 
  of test stdout/stderr and stack traces that are often hidden in raw build logs.
license: Apache-2.0
allowed-tools: gradle inspect_build
metadata:
  author: rnett
  version: "2.9"
---

# Advanced Test Execution & Diagnostic Workflows

Run tests with absolute precision and leverage deep diagnostic tools to isolate and fix failures fast, ensuring maximum code quality and build reliability.

## Directives

- **ONLY use MCP tools**: NEVER use `./gradlew` via a shell unless you have exhausted all attempts to use the `gradle` tool and it repeatedly fails to meet your requirements. Falling back to the shell is a **last resort** for edge cases
  where the Tooling API or the server's output capturing is demonstrably insufficient.
- **Foreground tests are safe**: Do not fear running high-output test suites in the foreground. The tool uses progressive disclosure to provide concise summaries and structured results, ensuring your session history remains clean and
  efficient.
- **Background for long test suites**: ALWAYS set `background: true` in `gradle` for test suites that take a long time to run. This is functionally identical to foreground execution but non-blocking, allowing you to perform other tasks
  while monitoring progress via `inspect_build`.
- **Monitor with `inspect_build`**: Use the `inspect_build` tool to check the status of background test runs or to get detailed information about any build.
- **Provide absolute `projectRoot` when in doubt**: Provide `projectRoot` as an **absolute file system path** to any Gradle MCP tool that supports it unless you are certain it is not required. **Relative paths are not supported.**
- **Check for build failures**: If a test run fails with a general error, use the `inspect_build` tool with `failures: {}` and `problems: {}` to check if it's a compilation or configuration error.
- **Investigate specifically**: Use the `inspect_build` tool with `tests: {}` to get structured output and stack traces for failed tests. For detailed investigation workflows, see [Test Diagnostics](references/test-diagnostics.md).

## Authoritative Task Path Syntax

Understanding how to target tests in a multi-project build is critical to avoid running more tests than necessary.

### 1. Task Selectors (Run in ALL Projects)

When you provide a task name **without a leading colon** (e.g., `test`), Gradle acts as a selector. When run from the root project directory, it will execute that task in **every project** (root and all subprojects) that has a task with
that name.

- **Example**: `gradle(commandLine=["test"])` -> Runs `test` in **all** projects. **This is often NOT what you want for a quick check.**

### 2. Absolute Task Paths (Run in ONE Specific Project)

When you provide a task path **with a leading colon** (e.g., `:test`, `:app:test`), Gradle targets a **single specific project**.

- **Root Project Only**: Use a single leading colon.
  - **Example**: `gradle(commandLine=[":test"])` -> Runs `test` in the **root project ONLY**.
- **Subproject Only**: Use the subproject name(s) separated by colons.
  - **Example**: `gradle(commandLine=[":app:test"])` -> Runs `test` in the **'app' subproject ONLY**.

## When to Use

- **Targeted Test Execution**: When you need to run specific tests or suites using precise filters (like `--tests`) to minimize feedback loops and resource consumption.
- **Rapid Failure Isolation**: When a build has failed and you need to perform high-resolution diagnostics on specific test failures, including accessing stdout/stderr and detailed stack traces.
- **Large-Scale Suite Management**: When running extensive test suites that benefit from managed background execution and real-time progress monitoring.
- **Comprehensive Build Health Check**: When you need to verify the correctness of a change across all subprojects or modules with authoritative results.

## Workflows

### Analyzing Test Results

The `inspect_build` tool is the primary way to perform deep dives into test results. Use the `mode="details"` setting with the `tests` option to retrieve:

- Full test status and execution duration.
- Failure details and stack traces.
- Metadata and attached files (e.g., screenshots for UI tests).
- **Console output (stdout/stderr)** for the specific test. Note that test stdout/stderr is often found here rather than in the build's general console output.

```json
{
  "buildId": "ID",
  "mode": "details",
  "tests": {
    "name": "com.example.MyTest.myTestMethod"
  }
}
```

### Running Specific Tests

1. Identify the project path (e.g., `:app`) and the test task name (usually `test`).
2. Use `gradle` with `commandLine` including `--tests` (e.g., `["test", "--tests", "com.example.MyTestClass*"]`).
3. If the tool reports failures, review the included console output.

### Investigating Test Failures

1. Identify the `BuildId` from the result.
2. If the build itself failed (not just tests), use `inspect_build(buildId=ID, failures={})`.
3. Use `inspect_build(buildId=ID, tests={outcome: "FAILED"})` to list all failed tests.
4. Use `inspect_build(buildId=ID, mode="details", tests={name: TNAME})` to see the full output and stack trace for a specific test.
5. For more advanced diagnostics, refer to [Test Diagnostics](references/test-diagnostics.md).

## Examples

### Run all tests in all projects

```json
{
  "commandLine": ["test"]
}
```

### Run a single test class in a specific subproject

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
- **Task Not Found**: In some projects (like Android), the test task might not be `test`. Use `inspect_build(tasks={})` or `gradle(commandLine=["tasks"])` to find the correct task name.
