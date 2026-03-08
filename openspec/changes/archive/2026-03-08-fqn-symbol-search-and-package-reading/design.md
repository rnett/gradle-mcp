## Context

`SymbolSearch` currently indexes symbols with exact `name`, analyzed `name_analyzed`, and exact `fqn` fields. `read_dependency_sources` is used to read files from the file system.

## Goals / Non-Goals

**Goals:**

- Support partial FQN search (e.g., searching for package segments).
- Support listing direct sub-packages and symbols within a given dot-separated package path via `read_dependency_sources` by querying the Lucene symbol index.
- Support glob-style wildcards for FQNs: `*` (one segment), `**` (multiple segments).
- Support full Lucene syntax in `SYMBOLS` search while keeping simple name search working.
- Maintain compatibility with existing tools.
- Implement universal case-sensitive search for all symbols.
- Ensure only declarations (not packages) are returned in symbol search results.
- Evaluate naming for the symbol search feature.

**Non-Goals:**

- Full-text search within class bodies (remains in `FULL_TEXT` search).

## Decisions

### 1. Improved Field Naming and Indexing

**Decision:** Standardize and update symbol index field names to:

- `name`: Case-sensitive analyzed simple name (`TextField` with a non-lowercasing analyzer).
- `fqn`: Case-sensitive analyzed fully qualified name (`TextField` with a non-lowercasing analyzer).
- `packageName`: Exact package name for efficient lookup (`StringField`).
  **Rationale:** These names are intuitive for direct use in Lucene queries. Universal case-sensitivity handles the casing requirements.

### 2. Case-Sensitive Analyzer for Symbol Fields

**Decision:** Use a non-lowercasing analyzer for both `name` and `fqn` fields.
**Rationale:** To distinguish PascalCase/camelCase declarations from lowercase packages.

### 3. Glob-Style FQN Wildcards Implementation

**Decision:** Transform glob patterns in FQN queries into appropriate Lucene queries (e.g., `RegexQuery`) for the `fqn` field.

- `*` matches `[^.]+`
- `**` matches `.*`
  **Rationale:** This fulfills the requirement for precise package segment matching.

### 4. Full Lucene Syntax for `SYMBOLS` search

**Decision:** Use `QueryParser` in `SymbolSearch` to support full Lucene syntax, similar to `FullTextSearch`.
**Implementation:** Default the query field to search across `name` and `fqn` if no field is specified.

### 5. Index-Backed Package Exploration via `read_dependency_sources`

**Decision:** Enhance `read_dependency_sources` to automatically resolve dot-separated package paths by querying the Lucene symbol index.
**Rationale:** This ensures correctness in Kotlin projects where the directory structure might not match the package name.
**Implementation:**

- Attempt literal file resolution first.
- If not found, use a specialized query in `SymbolSearch` to:
    1. Find all symbols with `packageName == targetPackage`.
    2. Find all immediate sub-package segments by searching for symbols whose `packageName` starts with `targetPackage.`.
- Present the result as a listing of symbols and sub-packages.

### 6. Symbol-Only Search Results

**Decision:** Filter the results of standard `SYMBOLS` search to only include declaration items. Packages themselves are never indexed as symbols in search results.

### 7. Feature Naming: Symbol Search vs. Declaration Search

**Decision:** Evaluate whether "symbol search" should be renamed to "declaration search" or a similar more descriptive term.
**Rationale:** "Symbol" can be ambiguous, while "Declaration" clearly communicates that it searches for code elements like classes, methods, and properties.
**Implementation Consideration:** If renamed, this would affect tool parameter names (`searchType`) and documentation.

## Risks / Trade-offs

- **[Performance] Index Size** → Analyzed FQN increases size but improves discoverability.
- **[Search Behavior] Case Sensitivity** → Users must use correct casing.
- **[Ambiguity] File vs. Package** → Prioritizing file-system resolution first mitigates most cases.
