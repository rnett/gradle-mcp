---
name: running_gradle_tests
description: >
  Executes and diagnoses tests at scale with high-precision filtering and authoritative, 
  surgical failure isolation. This skill is the STRONGLY PREFERRED way to manage 
  your testing lifecycle, offering features like surgical test selection via '--tests' flags, 
  managed background execution for long-running suites, and deep diagnostic integration 
  for rapid failure resolution. Use it to run specific test classes or methods, 
  monitor background test runs, or perform detailed post-mortem analysis of test failures. 
  Do NOT use for general build lifecycle tasks or dependency auditing.
license: Apache-2.0
allowed-tools: gradle inspect_build
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "3.3"
---

# Authoritative Gradle Test Execution & Diagnostics

Executes tests with absolute precision and leverage deep diagnostic tools to isolate and fix failures fast, ensuring maximum code quality and build reliability.

## Constitution

- **ALWAYS** use the `gradle` tool instead of `./gradlew` via shell.
- **ALWAYS** use the `--tests` flag for surgical test selection to minimize feedback loops.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **ALWAYS** prefer foreground execution (default) unless the test suite is extremely long-running (>2 minutes) or you explicitly intend to perform independent research while it proceeds.
- **ONLY** use `background: true` for managed background orchestration when context isolation and non-blocking exploration are required.
- **ALWAYS** use `inspect_build` with `mode: "details"` and `tests: { name: "..." }` to access full test output and stack traces.
- **NEVER** assume a test pass without verifying the results via `inspect_build` if failures occurred.

## Directives

- **ALWAYS use foreground for authoritative tests**: If you intend to wait for results, ALWAYS use foreground execution. It provides superior progressive disclosure and simpler control flow than starting a background build only to
  immediately call `inspect_build(wait=...)`.
- **Background ONLY for long test suites**: Use `background: true` ONLY for test suites that take a long time to run and you explicitly intend to perform independent research while they proceed.
- **Foreground tests are safe**: Do not fear running high-output test suites in the foreground. The `gradle` tool uses progressive disclosure to provide concise summaries and structured results, ensuring your session history remains clean
  and efficient.
- **Monitor with `inspect_build`**: Use `inspect_build` to check the status of background test runs or to retrieve structured output and stack traces for failed tests.
- **Check for environment failures**: If a test run fails with a general error, use `inspect_build(failures={})` to check for compilation or configuration issues.
- **Investigate specifically**: Use the `tests: {}` option in `inspect_build` to isolate specific failure details. For detailed diagnostic workflows, see the `test_diagnostics.md` reference.
- **Resolve `{baseDir}` manually**: If your environment does not automatically resolve the `{baseDir}` placeholder in reference links, treat it as the absolute path to the directory containing this `SKILL.md` file.

## Authoritative Test Selection Patterns

The `--tests` flag supports powerful, high-precision filtering. Use these patterns to minimize execution time and context noise.

### 1. Simple Filters

- **Exact Class**: `--tests com.example.MyTest`
- **Exact Method**: `--tests com.example.MyTest.myTestMethod`
- **Wildcard Method**: `--tests com.example.MyTest.test*` (Runs all methods starting with 'test')

### 2. Wildcard Filters (`*` and `?`)

- **Package Filter**: `--tests com.example.service.*` (Runs all tests in the 'service' package)
- **Class Prefix**: `--tests *IntegrationTest` (Runs all classes ending in 'IntegrationTest')
- **Character Wildcard**: `--tests com.example.Test?` (Matches Test1, TestA, etc.)

### 3. Syntax Rules

- **No Class Path**: Patterns match against the **fully qualified name** of the test class or method.
- **Multi-Filter**: You can provide multiple `--tests` flags to run a specific selection of tests.
    - `gradle(commandLine=["test", "--tests", "ClassA", "--tests", "ClassB"])`

## Authoritative Task Path Syntax

Understanding how to target tests in a multi-project build is critical to avoid running more tests than necessary.

### 1. Task Selectors (Recursive)

Providing `test` **without a leading colon** executes the test task in **every project** (root and all subprojects) that has one.

- **Example**: `gradle(commandLine=["test", "--tests", "MyTest"])` -> Searches for and runs 'MyTest' in **all** projects.

### 2. Absolute Task Paths (Targeted)

Providing a path **with a leading colon** targets a **single specific project**.

- **Root Project Only**: `gradle(commandLine=[":test", "--tests", "MyTest"])`
- **Subproject Only**: `gradle(commandLine=[":app:test", "--tests", "MyTest"])`

## When to Use

- **Targeted Test Execution**: When you need to run specific tests or suites using precise filters (like `--tests`) to minimize feedback loops.
- **Rapid Failure Isolation**: When a build has failed and you need high-resolution diagnostics, including stdout/stderr and detailed stack traces.
- **Large-Scale Suite Management**: When running extensive test suites that benefit from managed background execution and real-time progress monitoring.

## Workflows

### Running Specific Tests

1. Identify the project path (e.g., `:app`) and the test filter (e.g., `com.example.MyTestClass*`).
2. Call `gradle` with `commandLine` including `--tests`.
3. If the tool reports failures, review the included console output.

### Investigating Failures

1. Identify the `BuildId` from the result.
2. Use `inspect_build(buildId=ID, tests={outcome: "FAILED"})` to list all failed tests.
3. Use `inspect_build(buildId=ID, mode="details", tests={name: TNAME})` to see the full output and stack trace for a specific test.

## Examples

### Run a single test class in a specific subproject

```json
{
  "commandLine": [":module-a:test", "--tests", "com.example.service.MyServiceTest"]
}
// Reasoning: Using an absolute task path and exact class filter for the fastest possible feedback loop.
```

### List all failed tests in a build

```json
{
  "buildId": "build_20240301_130000_def456",
  "tests": {
    "outcome": "FAILED"
  }
}
// Reasoning: Using inspect_build to isolate only the failures from a large test suite.
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
// Reasoning: Retrieving the full stack trace and isolated stdout/stderr for a specific failure.
```

## Resources

- [Test Diagnostics]({baseDir}/references/test_diagnostics.md)
