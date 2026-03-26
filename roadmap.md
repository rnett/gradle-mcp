Steps are in order, from next to furthest.

## Completed

- Dependency update check has problems:
  - Progress for searching for dependency updates is only for configuring, and hangs for a while at the end.
  - Lots of [UPDATE CHECK SKIPPED] messages in output - unclear why and what to do about it
  - Searching maven central for versions shows a whole bunch of versions
  - Searching maven central with just a keyword (e.g. "koog") finds no results, when the web ui does
  - It may be better to not show deps per source set/whatever. Just show upgrades, and maybe where they are used? Maybe not even that.
- Progress for resolving dependencies for source downloading jumps large amounts at once
- Source searching should be able to filter dependencies by G:A (using regex or keywords or globs, too). Maybe search in directory, too?
- fqn and name fields need better docs in the search tool description.
- Full text search should somewhat prioritize declarations based on a cheap heuristic
- Single dependency download works for deps that aren't in your project
- Single dependency search can include transitive deps, and maybe dones by default?
- Better ways to search/read dependencies without configuring the whole project
- Option to include project sources in source index/search
- Periodic background cleanup of downloaded/cached files
- Test not found errors for test details should include a list of all tests that include the search query (basic string inclusion)
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