# Symbol Parsing Approaches Evaluation

This document evaluates the possible approaches for extracting symbols (declarations and Fully Qualified Names) from Kotlin, Java, and Groovy source files for indexing in the Gradle MCP server.

Given the constraints of the project, **three primary factors** must be balanced:

1. **Performance:** Indexing large codebases (like the Gradle source tree) must be fast (milliseconds per file, not seconds).
2. **Accuracy:** The parser must correctly identify nested blocks, context parameters, FQNs, and implicit syntax (like Groovy fields).
3. **JAR Size:** The resulting MCP server JAR must remain as small as possible to minimize distribution and startup overhead.

---

## 1. Language-Specific AST Parsers

This approach uses the official or standard parser for each language:

* **Kotlin:** `org.jetbrains.kotlin:kotlin-compiler-embeddable` (PSI/FIR)
* **Java:** `com.github.javaparser:javaparser-core`
* **Groovy:** `org.codehaus.groovy:groovy-astbuilder` or the internal compiler AST.

### Pros

* **100% Accuracy:** Perfectly understands the semantics, modifiers, and complex structures of the respective languages. No manual edge-case handling is required.
* **Pure JVM:** No native JNI dependencies are required, making cross-platform deployment simple.

### Cons

* **Massive JAR Size Bloat:** Including full compiler toolchains (especially `kotlin-compiler-embeddable` and the Groovy compiler) adds tens of megabytes to the final shadow JAR.
* **Poor Performance:** These parsers are designed for full semantic analysis and compilation, not lightweight symbol discovery. Building a full Abstract Syntax Tree (AST) for every file in a large zip archive will drastically increase
  memory usage and CPU time.

---

## 2. Tree-sitter (via `io.github.bonede:tree-sitter`)

Tree-sitter is a C-based incremental parsing system widely used for syntax highlighting and symbol extraction in modern tools. The `io.github.bonede:tree-sitter` library provides robust Java bindings that bundle native binaries for multiple
platforms.

### Pros

* **Extremely Fast:** For raw syntax tree generation, Tree-sitter is often 30x-40x faster than JVM AST parsers. It is highly optimized for lightweight structural analysis.
* **Error Tolerant:** It is designed to build a valid tree even if the source code contains syntax errors.
* **Unified API:** Symbols can be extracted using a unified query language (`scm` files) across languages.
* **Bundled Binaries:** The `bonede` artifacts (e.g., `tree-sitter-java`, `tree-sitter-kotlin`) automatically bundle and extract the necessary native JNI libraries for the JVM (`.dll`, `.so`, `.dylib`), eliminating deployment headaches.

### Cons

* **JAR Size Impact:** Shipping native binaries for various architectures inside the JAR will noticeably increase the final artifact size.
* **Groovy Grammar Missing:** While Java and Kotlin grammars are available pre-built (`tree-sitter-java`, `tree-sitter-kotlin`), there is no pre-built `tree-sitter-groovy` artifact in Maven Central. We would need to either build it
  manually, use the custom state-machine fallback just for Groovy, or ignore Groovy FQNs.

---

## 3. Custom Tokenizer + State Machine (Current Approach)

This is the approach currently being prototyped in `SourceCodeTokenizer` and `SymbolFilter`. It uses a custom, single-pass lexer and a heuristic state machine to identify declarations.

### Pros

* **Zero Dependencies:** Adds exactly 0 bytes to the external dependency footprint. The JAR size remains minimal.
* **Extremely Fast:** It performs a single pass over the character array without building an AST, allocating very few objects. Memory overhead is effectively zero.
* **Pure JVM:** 100% Kotlin, completely cross-platform.

### Cons

* **Fragile & High Maintenance:** It relies on heuristics. As seen during prototyping, it struggles with language edge cases like Kotlin `context` receivers, `companion` blocks, Groovy's implicit field syntax, and complex generic nesting.
* **Accuracy Risks:** FQN tracking requires manually managing a depth stack based on punctuation `{ } ( )`, which can easily become desynchronized by unexpected syntax or unbalanced brackets in comments/strings.

---

## 4. Custom Lightweight Recursive Descent Parser

Instead of a heuristic state machine, we write a formal (but highly simplified) recursive descent parser tailored *exclusively* for extracting symbols and FQNs.

### Pros

* **Zero Dependencies:** Like the state machine, this adds no external libraries, keeping the JAR size minimal.
* **High Performance:** Because it only parses structural elements (classes, interfaces, methods, properties) and skips the bodies of functions entirely, it remains much faster than a full AST parser.
* **Improved Accuracy:** A formal recursive descent parser handles nested blocks, generics, and modifiers predictably. It is far more robust than a state machine tracking depth integers.

### Cons

* **Development Effort:** Writing and maintaining a custom parser for the structural subsets of Java, Kotlin, and Groovy is a non-trivial engineering task.
* **Language Evolution:** As Kotlin and Java evolve (e.g., `context` receivers, Java `record`s), the custom parser must be manually updated to support new structural syntax.

---

## 5. General Parser Generators (e.g., ANTLR)

ANTLR is a powerful parser generator that reads grammar files (`.g4`) and generates Java parsing code during the build process.

### Pros

* **Existing Grammars:** Community-maintained grammars for Java, Kotlin, and Groovy already exist, saving the effort of writing them from scratch.
* **Reasonable JAR Size:** The ANTLR runtime dependency is relatively small (~300KB), which is much better than bundling full compiler toolchains.
* **High Accuracy:** If the grammar is correct, the parsing is completely accurate.

### Cons

* **Build Complexity:** Introduces a code generation step to the Gradle build.
* **Performance:** ANTLR builds full parse trees by default. While tunable, it can be slower and more memory-intensive than a specialized, hand-written partial parser.
* **Grammar Lag:** Community grammars often lag behind the latest language features (e.g., Kotlin `context` parameters or experimental features).

---

## 6. Parser Combinator Libraries (e.g., better-parse)

Libraries like [better-parse](https://github.com/h0tk3y/better-parse) provide a pure-Kotlin DSL to build parsers programmatically using combinators.

### Pros

* **Very Lightweight:** `better-parse` is tiny and adds virtually no bloat to the JAR size.
* **Pure Kotlin / No Generation:** Unlike ANTLR, there is no build-time generation step; the grammar is just Kotlin code.
* **Flexible:** We could write a "fuzzy" grammar that only parses declarations and skips function bodies, balancing performance and effort.

### Cons

* **We Write the Grammar:** We still have to manually author the structural grammar for Java, Kotlin, and Groovy using the DSL.
* **Performance Overhead:** Parser combinators can suffer from performance issues (backtracking, heavy object allocation for tokens/nodes) compared to a tightly hand-written recursive descent parser, which is critical when indexing millions
  of lines.

---

## Summary

If **JAR size** and **Performance** are the absolute highest priorities, pulling in heavy AST libraries (Option 1) or native binaries (Option 2) is highly undesirable.

Therefore, the choice falls between:

* Maintaining the fragile **State Machine (Option 3)**
* Investing the engineering effort to build a robust, lightweight **Custom Recursive Descent Parser (Option 4)**
* Using **ANTLR (Option 5)** with existing community grammars (accepting the build complexity and potential grammar lag)
* Writing a partial grammar using **better-parse (Option 6)** (balancing small JAR size with the effort of writing the grammar ourselves).

Option 4 or Option 6 provide the best balance of zero/low-dependency deployment and acceptable accuracy, with Option 4 likely winning on raw performance.