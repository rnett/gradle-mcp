Steps are in order, from next to furthest.

- Polish skill and tool descriptions to minimize unnecessary context use
- Full text search should somewhat prioritize declarations based on a cheap heuristic
- Single dependency download works for deps that aren't in your project
- Better ways to search/read dependencies without configuring the whole project
- Option to include project sources in source index/search
- Expose absolute paths of downloaded deps for things like rg or ast-grep
- Periodic background cleanup of downloaded/cached files
- Semantic search of sources, etc, using a vector index in lucene. The problem is creating the embeddings, since MCP sampling doesn't support it and isn't widely supported.
- There is a `--non-interactive` flag coming in Gradle 9.5: https://github.com/gradle/gradle/pull/36880. Detect the version and add it.  `NONINTERACTIVE` env var might be better.
- A "Gradle doctor" like skill with best practices embedded in it (via gradle task), or enhance the expert skill.
- Dedicated "render this compose preview" tool that takes the file+function name or line number
- More skills in general.
- Investigate how tests with `@Ignore` are reported, compare with IDE.
- Group test results by test suite/class in summaries for better readability.
- Support for test retry reporting and highlighting flaky tests.
- Kotlin version independent REPL
- Test with continuous builds