# Analysis: Tree-sitter Indexing vs. Full Text Search with Post-filtering

## Background

We are evaluating two distinct architectural approaches for finding symbol declarations (classes, methods, fields) across large project dependencies (like the Gradle source codebase).

1. **AOT Indexing (Tree-sitter)**: Parse every source file Ahead-Of-Time (AOT) using Tree-sitter during the initial dependency extraction. Store the extracted symbols and their exact locations in a dedicated Lucene index.
2. **JIT Post-filtering (Full Text + Regex/Heuristics)**: Do *not* build a dedicated symbol index. Instead, rely entirely on the existing, fast Lucene Full Text Search index. When a user searches for a symbol, execute a full-text query for
   that name, and Just-In-Time (JIT) parse/filter the resulting snippet matches using regex or lightweight heuristics to determine if the match is an actual declaration.

## 1. AOT Indexing (Tree-sitter)

### Pros

* **100% Precision & Recall:** By parsing the AST, we know with absolute certainty if `String name` is a class field declaration or just a local variable inside a method. We never return false positives.
* **FQN Accuracy:** We can perfectly construct the Fully Qualified Name (FQN) by walking the AST hierarchy during indexing.
* **Blazing Fast Search:** The search operation hits a dedicated, heavily optimized Lucene index containing *only* symbols. Search latency is effectively zero.

### Cons

* **Indexing Overhead:** Parsing every single file with Tree-sitter during dependency extraction takes CPU time. Even though Tree-sitter is fast, parsing millions of lines of code is noticeably slower than just dumping text into Lucene.
* **Storage Overhead:** We maintain a second Lucene index alongside the full-text index, duplicating some data.
* **Dependency Bloat:** Requires bundling Tree-sitter native JNI binaries, increasing the JAR size.

## 2. JIT Post-filtering (Full Text Search + Regex)

In this model, a search for the symbol "Configuration" executes a standard Lucene full-text query for "Configuration". We then take the top `N` snippet results and apply regex rules (e.g., `interface Configuration`, `class Configuration`)
to filter out usages (e.g., `Configuration conf = new...`) and only return declarations.

### Pros

* **Zero Indexing Overhead:** We do absolutely no extra work during dependency extraction. The existing Full Text Search index is reused for free.
* **Zero Storage Overhead:** No separate symbol index exists on disk.
* **Zero Dependencies:** No need for Tree-sitter, JNI wrappers, or native binaries. The JAR size remains untouched.

### Cons

* **Recall Risk (The "Hidden Needle" Problem):** This is the fatal flaw. Lucene full-text search ranks results based on TF-IDF/BM25 (term frequency). If a symbol is heavily *used* across a codebase, the actual *declaration* might be ranked
  at position #500. If we only post-filter the top 100 hits, the declaration will be completely missing from the results. We would have to fetch and filter thousands of hits to guarantee we found the declaration, destroying the performance
  of the search.
* **FQN Inaccuracy:** It is nearly impossible to accurately determine the FQN of a symbol from an isolated text snippet. We would have to re-read the entire source file from disk *during* the search to parse the `package` and enclosing
  `class` declarations, introducing massive disk I/O latency.
* **Precision Risk:** Regex heuristics are notoriously bad at distinguishing between a method declaration `void run()` and a method call `run()` if the snippet is cut off abruptly.

## Conclusion

While **JIT Post-filtering** is extremely attractive due to its zero-cost indexing and zero-dependency footprint, it suffers from a critical architectural flaw: **Recall Risk**.

Because a widely used symbol will have hundreds of "usage" matches polluting the full-text search results, the actual "declaration" match is easily buried. To find the declaration reliably, we would have to retrieve and regex-filter an
unacceptably large number of full-text hits, completely negating the speed advantage of the fast Lucene search. Furthermore, generating accurate FQNs on the fly would require expensive disk reads.

Therefore, **AOT Indexing (whether via Tree-sitter or a custom parser)** remains the only viable path for a reliable, FQN-aware symbol search feature. The cost paid at indexing time guarantees precision, recall, and zero-latency search
queries.