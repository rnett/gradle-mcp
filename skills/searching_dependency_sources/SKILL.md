---
name: searching_dependency_sources
description: >-
  The ONLY authoritative way to explore, navigate, and understand the internal 
  implementation, APIs, and symbols of any project dependency. Provides 
  high-performance symbol, full-text, and glob searching across the entire 
  dependency graph. Use for finding class/interface definitions, method 
  signatures, constant values, or resource files (XML, JSON) contained within 
  external libraries. Essential for discovering how to use an API by reading 
  its source or finding where a specific symbol is defined. Do NOT use for 
  project source code (use grep_search), Gradle Build Tool internals 
  (use researching_gradle_internals), or Maven Central discovery 
  (use managing_gradle_dependencies).
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.3"
---

# Authoritative Dependency Source & API Exploration

Explores, navigates, and analyzes the internal logic, APIs, and symbol implementations of external libraries with absolute precision using high-performance, indexed searching.

## Critical Rules

- **ALWAYS** use `search_dependency_sources` as the primary discovery tool for external library code.
- **ALWAYS** provide absolute paths for `projectRoot`.
- **NEVER** use `gradleSource: true` in this skill; use `researching_gradle_internals` for Gradle's internal implementation.
- **NEVER** use generic shell tools like `grep` or `find` on the local directory to find dependency sources; they reside in remote caches managed by Gradle.
- **ALWAYS** escape Lucene special characters (`:`, `=`, `+`, `-`, `*`, `/`) in `FULL_TEXT` searches using a backslash (e.g., `\:`) or double quotes.
- **ALWAYS** use `read_dependency_sources` once a specific file path has been identified via search.

## Directives

- **Identify Search Mode**:
    - Use `SYMBOLS` (default) for finding class, interface, or method declarations.
    - Use `FULL_TEXT` for searching literal strings, constants, or specific code patterns within files.
    - Use `GLOB` for locating files by name or extension (e.g., `**/*.xml`).
- **Scope Surgically**: Use `projectPath`, `configurationPath`, or `sourceSetPath` to narrow the search and improve performance if the target library's context is known.
- **Refresh Indices**: Use `fresh: true` if project dependencies have recently changed to ensure the index is up-to-date.
- **Analyze Implementation**: Use `read_dependency_sources` to retrieve the implementation logic. If the file is large, use `pagination` to read specific sections.
- **Trace Symbols Authoritatively**: When encountering an unknown symbol in your project code, use `SYMBOLS` search to jump directly to its definition in the library. This is the only reliable way to understand its exact behavior and
  available methods.
- **Use `envSource: SHELL` if environment variables are missing**: If the tool fails to find expected environment variables (e.g., `JAVA_HOME` or specific JDKs), it may be because the host process started before the shell environment was
  fully loaded. Use `fresh: true` and ensure the project root has a valid `gradle-daemon-jvm.properties` or set environment variables directly if needed. Note that these tools implicitly use Gradle to resolve dependencies.
- **Resolve `{baseDir}` manually**: If the environment does not automatically resolve the `{baseDir}` placeholder in reference links, treat it as the absolute path to the directory containing this `SKILL.md` file.

## When to Use

- **API & Symbol Discovery**: When you need to find the implementation, signature, or documentation of a class, interface, or method imported from a library.
- **Library Usage Research**: When understanding how to use a library's API by reading its internal implementation or looking for usage patterns in its source.
- **Internal Logic Auditing**: When researching how a dependency handles specific operations, edge cases, or performance-critical logic.
- **Resource File Location**: When searching for configuration files (XML, JSON, properties) or metadata (AndroidManifest.xml) packaged within library jars.
- **Constant & Literal Research**: When searching for specific constant values, error strings, or literal keys within external code.

## Workflows

### 1. Tracing a Symbol from Project Code

1. Identify the symbol name (e.g., `JsonConfiguration`) or fully qualified name from an import.
2. Call `search_dependency_sources(query="<SymbolName>", searchType="SYMBOLS")`.
3. Identify the correct file path from the results.
4. Call `read_dependency_sources(path="<path>")` to analyze the implementation.

### 2. Discovering API Usage through Source

1. Search for a known entry point (e.g., a constructor or main class) using `SYMBOLS`.
2. Once the file is found, use `read_dependency_sources` to read its source.
3. Look for internal calls, helper methods, or factory patterns to understand the library's preferred usage.

### 3. Searching for Constants or Error Codes

1. Identify the constant name or a snippet of an error message.
2. Call `search_dependency_sources(query="\"<text>\"", searchType="FULL_TEXT")`.
3. Review matches to find where the value is defined or used.

## Examples

### Search for a specific class definition

```json
{
  "query": "JsonConfiguration",
  "searchType": "SYMBOLS"
}
// Reasoning: Using SYMBOLS search to find the declaration of a specific class across all project dependencies.
```

### Trace a method signature from a symbol name

```json
{
  "query": "encodeToString",
  "searchType": "SYMBOLS"
}
// Reasoning: Finding all definitions of 'encodeToString' across dependencies to identify the correct implementation.
```

### Search for a constant value assignment

```json
{
  "query": "DEFAULT_TIMEOUT_MS \\: 5000",
  "searchType": "FULL_TEXT"
}
// Reasoning: Using FULL_TEXT with escaped colon to find a specific constant assignment.
```

### Locate all Android Manifest files in dependencies

```json
{
  "query": "**/AndroidManifest.xml",
  "searchType": "GLOB"
}
// Reasoning: Using GLOB search to find resource files by pattern across the dependency graph.
```

### Read a specific dependency source file

```json
{
  "path": "kotlinx/serialization/json/Json.kt"
}
// Reasoning: Reading the implementation of a known class path identified from previous search results.
```

## Resources

- **Lucene Query Syntax**: Refer to the tool description for `search_dependency_sources` for details on complex queries and escaping.
