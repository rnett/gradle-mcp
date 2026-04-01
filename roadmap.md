Steps are in order, from next to furthest.

- Buildscript deps shouldn't be included unless asked for for sources.
- Selecting tests doesn't seem to work
- The agent likes to find the full path for a file in the root deps, and then try to read it by specifying the dependency + the path in the dependency (as opposed to the dependency's path in the view root). It has lots of trouble with the
  view dependency structure. `fd` may not be following the
  links.
- REPL seems to have issues with JVM versions for Kotlin/JVM project. I suspect the compilation jvm target is not propagated correctly. See docs/[gradle-mcp-repl-jvm-target-bug.md](docs/gradle-mcp-repl-jvm-target-bug.md)
- Searching within inspect build results, or maybe just write them to a file. Some tests have too much output to go through manually.
- Dependency collection task is not CC compatible
- Minimize presence of things (tests, problems, etc) in build summaries when 0 are present.
- There is a `--non-interactive` flag coming in Gradle 9.6: https://github.com/gradle/gradle/pull/36880. Detect the version and add it.  `NONINTERACTIVE` env var might be better and we can do that now.
- Include https://docs.gradle.org/9.5.0-rc-1/release-notes.html#task-provenance-in-reports-and-failure-messages.
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