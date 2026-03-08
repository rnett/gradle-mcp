# Comparison: tree-sitter-ng vs. java-tree-sitter

## Background

We evaluated two major JVM binding libraries for Tree-sitter:

1. **`bonede/tree-sitter-ng`** (published as `io.github.bonede:tree-sitter-*`)
2. **`seart-group/java-tree-sitter`** (published as `ch.usi.si.seart:java-tree-sitter`)

## Feature Comparison

| Feature               | `tree-sitter-ng` (`bonede`)                                                                                                | `java-tree-sitter` (`seart-group`)                                                                     |
|:----------------------|:---------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------|
| **OS Support**        | Windows, macOS (x86_64, aarch64), Linux (x86_64, aarch64)                                                                  | macOS, Linux (No native Windows support)                                                               |
| **Grammar Packaging** | **Modular.** Each grammar is a separate Maven artifact (e.g., `tree-sitter-java`). You only pay for what you use.          | **Monolithic.** Bundles 65+ grammars as git submodules directly into the core library.                 |
| **Native Binaries**   | Bundles pre-compiled JNI `.dll`, `.so`, and `.dylib` files into the grammar artifacts, extracted automatically at runtime. | Bundles compiled native libraries for macOS and Linux, compiled together using a Python/Docker script. |
| **Kotlin Support**    | Excellent. Used extensively in Kotlin projects; Kotlin grammar available (`tree-sitter-kotlin`).                           | Includes `tree-sitter-kotlin` in its massive bundled list.                                             |
| **API Coverage**      | Claims 100% coverage of the native C API, including advanced S-expression queries and cursors.                             | Provides a robust Java wrapper for core parsing and querying features.                                 |

## Why `tree-sitter-ng` (`bonede`) is Better for Our Use Case

Given our specific constraints (Performance, Accuracy, and JAR Size on a Windows-first MCP server), `tree-sitter-ng` is the clear winner for several reasons:

1. **Windows Support:** Our primary environment for the Gradle MCP server is Windows (`win32`). `java-tree-sitter` explicitly states it only supports macOS and Linux out of the box, meaning it would likely fail or require complex WSL
   workarounds for Windows users. `tree-sitter-ng` fully supports Windows.
2. **JAR Size (Modularity):** `java-tree-sitter` bundles over 65 languages. This monolithic approach would massively bloat our distribution JAR with parsers for Ada, Ruby, Zig, etc., which we do not need. `tree-sitter-ng` is entirely
   modular. We only include `tree-sitter-java` and `tree-sitter-kotlin`, keeping the native binary footprint strictly limited to what we actually use.
3. **Cross-Compilation Toolchain:** `tree-sitter-ng` uses Zig for clean, reproducible cross-compilation of native binaries across all major operating systems and CPU architectures. This provides a high degree of confidence in the native
   artifacts we are downloading from Maven Central.

**Conclusion:** We should stick with the plan to use `io.github.bonede:tree-sitter` and its modular grammar dependencies.