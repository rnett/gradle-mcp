Steps are in order, from next to furthest.

- Multi-language/not language limited support for tree-sitter integration
- Agents use gradleSource with the source tools way too often
- Add `--enable-native-access=ALL-UNNAMED` like we have for the vector api
- The gradle tool isn't used as often as it should
- The agent often has to think about what to use as the project root
- The indexing progress for full text search goes well beyond 100%/keeps going for a while once it hits 100%.
- Include https://docs.gradle.org/9.5.0-rc-1/release-notes.html#task-provenance-in-reports-and-failure-messages.
- Include JDK sources in dependencies
- Tree sitter query language queries over deps
- Include project sources in dep views/read/search
- A "Gradle doctor" like skill with best practices embedded in it (via gradle task), or enhance the expert skill.
- Dedicated "render this compose preview" tool that takes the file+function name or line number
- More skills in general.
- Investigate how tests with `@Ignore` are reported, compare with IDE.
- Support for test retry reporting and highlighting flaky tests.
- Kotlin version independent REPL
- Test with continuous builds

- Move dep source downloading and searching out to a new MCP that uses ORT
- deps should have a file with their dependencies + consumers
- Semantic search of sources, etc, using a vector index in lucene. The problem is creating the embeddings, since MCP sampling doesn't support it and isn't widely supported.
- Full text search should somewhat prioritize declarations based on a cheap heuristic
- Option to return max one result per file, or even just the files