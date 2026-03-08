# Parsing Library Evaluation for Symbol Indexing

## Background

The goal is to extract declarations (classes, methods, fields) and their Fully Qualified Names (FQNs) from Kotlin, Java, and Groovy source files to index them in Lucene.

Currently, we are using a custom `SourceCodeTokenizer` and state machine (`SymbolFilter`). While fast and dependency-free, this approach is fragile when dealing with complex language features (nested blocks, context parameters, implicit
Groovy syntax).

## Option 1: Tree-sitter (via JNI)

Tree-sitter is a C-based incremental parsing system used by GitHub and modern IDEs (like Neovim and Zed).

* **Pros:**
    * **Extremely Fast:** For raw syntax tree generation, it is up to 30x-40x faster than traditional JVM AST parsers like JavaParser. This is ideal for our bulk-indexing use case.
    * **Error Tolerant:** It builds a valid tree even if the source code has syntax errors.
    * **Unified API:** We can use the same query language (`scm` files) to extract symbols across Java, Kotlin, and Groovy.
* **Cons:**
    * **JNI Dependency:** Requires native libraries (`.dll`, `.so`, `.dylib`) which complicates deployment and distribution of the Gradle MCP server.
    * **Groovy Support:** The Groovy grammar is less mature than Java or Kotlin.

## Option 2: Language-Specific AST Parsers

This involves using the "official" or standard parser for each language:

* **Kotlin:** `org.jetbrains.kotlin:kotlin-compiler-embeddable` (already in `libs.versions.toml`) to use the Kotlin PSI or FIR.
* **Java:** `com.github.javaparser:javaparser-core`
* **Groovy:** `org.codehaus.groovy:groovy-astbuilder`

* **Pros:**
    * **100% Accurate:** Perfectly understands the language semantics, modifiers, and complex structures.
    * **Pure JVM:** No native dependencies, easy to include.
* **Cons:**
    * **Performance:** These parsers are designed for full compilation. Building the full AST/PSI for every file in a massive zip (like the Gradle source codebase) will be significantly slower and use much more memory than our current
      tokenizer.
    * **Dependency Bloat:** Pulling in JavaParser and Groovy AST adds significant weight to the MCP server.

## Option 3: Refine the Custom Tokenizer (Current Path)

We continue refining the custom lexer (`SourceCodeTokenizer`) and state machine (`SymbolFilter`).

* **Pros:**
    * **Zero Dependencies:** Keeps the MCP server lightweight.
    * **Extremely Fast:** The single-pass lexer without full AST construction is highly performant.
* **Cons:**
    * **Maintenance Burden:** We have to manually patch edge cases (like `companion`, `context`, `record`, missing semicolons) as we discover them. It will never be a perfect parser.

## Recommendation

For indexing *millions* of lines of code where we only care about symbol discovery (not type resolution), **Performance** and **Memory** are the critical constraints.

If we are willing to accept the JNI complexity, **Tree-sitter** is the gold standard for this exact problem.

If we want to remain pure JVM and avoid the massive overhead of full compiler ASTs, **Refining the Custom Tokenizer** is the most pragmatic choice, provided we add enough test coverage for edge cases.