Steps are in order, from next to furthest.

- Add `--enable-native-access=ALL-UNNAMED` like we have for the vector api
- Tree sitter query language queries over deps
- Include project sources in dep views/read/search
- Always include gradle sources for buildscript dependency scopes?
- Regex based filtering for dependencies sources, applied to coordinates. More consistent than the current hacky version.
- Tests take forever and spawn (and orphan) a lot of Gradle daemons. Indexing JDK sources is certainly one cause
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