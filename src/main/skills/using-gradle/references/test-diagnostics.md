<!--
class: authored-local
skill: using-gradle
-->
# Test Diagnostics

Runs tests with high-precision filtering and investigates test failures using `query_build`.

## Test Selection (`--tests`)

The `--tests` flag supports powerful, high-precision filtering:

| Pattern | Example | Matches |
|---------|---------|---------|
| Exact Class | `com.example.MyTest` | Single test class |
| Exact Method | `com.example.MyTest.myMethod` | Single test method |
| Wildcard Method | `com.example.MyTest.test*` | Methods starting with `test` |
| Package Filter | `com.example.service.*` | All tests in package |
| Class Suffix | `*IntegrationTest` | Classes ending in `IntegrationTest` |
| Character Wildcard | `com.example.Test?` | Test1, TestA, etc. |
| Multi-Filter | `--tests ClassA --tests ClassB` | Multiple classes |

Patterns match against the **fully qualified name** of the test class or method.

## Running Tests

### Run All Tests in Root Project

```json
{
  "commandLine": [":test"]
}
```

### Run Specific Test Class in Subproject

```json
{
  "commandLine": [":app:test", "--tests", "com.example.service.MyServiceTest"]
}
```

### Run Multiple Test Classes

```json
{
  "commandLine": ["test", "--tests", "ClassA", "--tests", "ClassB"]
}
```

### Run All Integration Tests

```json
{
  "commandLine": ["integrationTest", "--tests", "*IntegrationTest"]
}
```

## Investigating Test Failures

### Step 1: Identify Failed Tests

```json
{
  "buildId": "BUILD_ID",
  "kind": "TESTS",
  "outcome": "FAILED"
}
```

Lists all failed tests with summary information.

### Step 2: Get Full Test Output

```json
{
  "buildId": "BUILD_ID",
  "kind": "TESTS",
  "query": "com.example.MyTest.myFailingMethod"
}
```

Returns the full output and stack trace for a specific test.

### Step 3: Inspect Console for Context

```json
{
  "buildId": "BUILD_ID",
  "kind": "CONSOLE",
  "query": "MyTest"
}
```

Search console logs for additional context around the test execution.

## Critical Rules

- **ALWAYS** use `query_build(kind="TESTS", query="FullTestName")` for test output.
- **NEVER** use `taskPath` or `captureTaskOutput` to investigate specific test failures — they provide the overall task log which is often truncated.
- **NEVER** use `--rerun-tasks` unless investigating cache corruption; prefer `--rerun` for individual tasks.
- **Use `testIndex`** when multiple tests share the same name to select the correct one.

## Re-running Failed Tests

Use `--rerun` to re-execute a specific task:

```json
{
  "commandLine": [":app:test", "--tests", "com.example.MyTest", "--rerun"]
}
```

## Examples

### Run a single test and investigate failure

```json
{ "commandLine": [":app:test", "--tests", "com.example.service.UserServiceTest"] }
// If failure:
{ "buildId": "ID", "kind": "TESTS", "outcome": "FAILED" }
// Then:
{ "buildId": "ID", "kind": "TESTS", "query": "com.example.service.UserServiceTest" }
```
